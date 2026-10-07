package sollecitom.plugins.conventions.task.jib

import com.google.cloud.tools.jib.gradle.JibExtension
import com.google.cloud.tools.jib.gradle.JibPlugin
import com.google.cloud.tools.jib.gradle.JibTask
import com.google.cloud.tools.jib.gradle.PlatformParameters
import com.google.cloud.tools.jib.gradle.PlatformParametersSpec
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.nativeplatform.platform.OperatingSystem
import org.gradle.nativeplatform.platform.internal.ArchitectureInternal
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import java.time.Instant
import java.util.AbstractList
import java.util.AbstractMap

/** Convention plugin that configures Jib for building Docker images. Automatically detects the host platform (including Apple Silicon) and configures the target architecture accordingly. */
abstract class JibDockerBuildConvention : Plugin<Project> {

    override fun apply(project: Project) = with(project) {

        pluginManager.apply(JibPlugin::class)
        val settings = project.extensions.create<Extension>("jibDockerBuildConvention")
        val mainSourceSet = extensions.getByType<SourceSetContainer>().named("main")
        val imageFingerprintFile = layout.buildDirectory.file(imageFingerprintFileName)
        val imageFingerprint = imageFingerprintFile.map { it.asFile.readText().trim() }
        val imageReferences = settings.serviceImageName.zip(settings.tags.orElse(Extension.defaultTags)) { imageName, tags -> imageReferences(imageName, tags) }
        tasks.withType<JibTask>().configureEach {
            dependsOn(imageFingerprintTaskName)
        }
        tasks.named("jibDockerBuild") {
            notCompatibleWithConfigurationCache("Jib's BuildDockerTask stores Project state and currently fails configuration-cache reuse.")
            onlyIf("the local Docker image is missing or was built from different inputs") { !localImagesHaveFingerprint(imageReferences.get(), imageFingerprint.get()) }
        }
        tasks.register<WriteJibImageFingerprintTask>(imageFingerprintTaskName) {
            description = "Writes a stable fingerprint for the locally built Jib image."
            group = "build"
            runtimeClasspath.from(mainSourceSet.map { it.runtimeClasspath })
            fingerprintFile.set(imageFingerprintFile)
            starterClassFullyQualifiedName.set(settings.starterClassFullyQualifiedName)
            dockerBaseImage.set(settings.dockerBaseImage)
            serviceImageName.set(settings.serviceImageName)
            reproducibleBuild.convention(settings.reproducibleBuild.orElse(Extension.defaultReproducibleBuild))
            volumes.convention(settings.volumes.orElse(Extension.defaultVolumes))
            jvmFlags.convention(settings.jvmFlags.orElse(Extension.defaultJvmFlags))
            args.convention(settings.args.orElse(Extension.defaultArgs))
            tags.convention(settings.tags.orElse(Extension.defaultTags))
            labels.convention(settings.labels.orElse(Extension.defaultLabels))
            environment.convention(settings.environment.orElse(emptyMap()))
        }

        val nonReproducibleTimestampHolder = object {
            var value: String? = null
        }
        val creationTimeProvider = providers.provider {
            if (settings.reproducibleBuildValue) {
                EPOCH_TIMESTAMP
            } else {
                nonReproducibleTimestampHolder.value ?: Instant.now().toString().also { nonReproducibleTimestampHolder.value = it }
            }
        }
        val filesModificationTimeProvider = providers.provider {
            if (settings.reproducibleBuildValue) {
                EPOCH_PLUS_SECOND_TIMESTAMP
            } else {
                nonReproducibleTimestampHolder.value ?: Instant.now().toString().also { nonReproducibleTimestampHolder.value = it }
            }
        }

        // Keep the Jib extension wiring declarative and avoid afterEvaluate here. Jib still
        // exposes some container settings as plain List/Map setters, so we bridge those through
        // read-only lazy adapters instead of forcing consuming builds into late mutation.
        extensions.configure<JibExtension> {
            container {
                setArgs(lazyList(settings.args.orElse(Extension.defaultArgs)))
                setJvmFlags(settings.jvmFlags.orElse(Extension.defaultJvmFlags))
                setVolumes(lazyList(settings.volumes.orElse(Extension.defaultVolumes)))
                setEnvironment(lazyMap(settings.environment.map { value: Map<String, String> -> value.toSortedMap() as Map<String, String> }.orElse(emptyMap())))
                user = Extension.defaultUser
                setFormat(Extension.defaultImageFormat)
                creationTime.set(creationTimeProvider)
                filesModificationTime.set(filesModificationTimeProvider)
                labels.set(settings.labels.orElse(Extension.defaultLabels).zip(imageFingerprint) { labels, fingerprint -> labels + (imageFingerprintLabel to fingerprint) })
                containerizingMode = "exploded"
                setMainClass(settings.starterClassFullyQualifiedName)
            }
            from {
                setImage(settings.dockerBaseImage)
                platforms {
                    configureForOperatingSystem(currentOperatingSystem, currentArchitecture)
                }
            }
            to {
                setImage(settings.serviceImageName)
                setTags(settings.tags.map(List<String>::toSet).orElse(emptySet()))
            }
        }
    }

    private fun imageReferences(imageName: String, tags: List<String>) = listOf(imageName) + tags.map { tag -> "${imageName.withoutTag()}:$tag" }

    private fun String.withoutTag() = if (':' in substringAfterLast('/')) substringBeforeLast(':') else this

    private fun localImagesHaveFingerprint(imageReferences: List<String>, fingerprint: String): Boolean {

        val inspection = ProcessBuilder(listOf("docker", "image", "inspect", "--format", "{{ index .Config.Labels \"$imageFingerprintLabel\" }}") + imageReferences)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        val labelValues = inspection.inputStream.bufferedReader().readLines()
        return inspection.waitFor() == 0 && labelValues.size == imageReferences.size && labelValues.all { it.trim() == fingerprint }
    }

    private val currentOperatingSystem: OperatingSystem get() = DefaultNativePlatform.getCurrentOperatingSystem()
    private val currentArchitecture: ArchitectureInternal get() = DefaultNativePlatform.getCurrentArchitecture()

    private val Extension.reproducibleBuildValue: Boolean get() = reproducibleBuild.getOrElse(Extension.Companion.defaultReproducibleBuild)

    /**
     * Extension for configuring Jib Docker image builds.
     *
     * Required properties: [starterClassFullyQualifiedName], [dockerBaseImage], [serviceImageName].
     * All other properties have sensible defaults (OCI format, "nobody" user, reproducible build).
     */
    abstract class Extension {

        /** Fully qualified name of the main class (required). */
        abstract val starterClassFullyQualifiedName: Property<String>
        /** Base Docker image to build from (required). */
        abstract val dockerBaseImage: Property<String>
        /** Target image name for the built image (required). */
        abstract val serviceImageName: Property<String>

        /** Whether to produce reproducible builds with fixed timestamps. Defaults to true. */
        @get:Optional
        abstract val reproducibleBuild: Property<Boolean>

        /** Container volumes. Defaults to empty. */
        @get:Optional
        abstract val volumes: ListProperty<String>

        /** JVM flags passed to the containerized application. Defaults to empty. */
        @get:Optional
        abstract val jvmFlags: ListProperty<String>

        /** Application arguments. Defaults to empty. */
        @get:Optional
        abstract val args: ListProperty<String>

        /** Docker image tags. Defaults to empty. */
        @get:Optional
        abstract val tags: ListProperty<String>

        /** Image labels. Defaults to empty. */
        @get:Optional
        abstract val labels: MapProperty<String, String>

        /** Environment variables for the container. */
        @get:Optional
        abstract val environment: MapProperty<String, String>

        internal companion object {
            const val defaultReproducibleBuild = true
            const val defaultImageFormat = "OCI"
            const val defaultUser = "nobody"
            val defaultTags = emptyList<String>()
            val defaultArgs = emptyList<String>()
            val defaultJvmFlags = emptyList<String>()
            val defaultVolumes = emptyList<String>()
            val defaultLabels = emptyMap<String, String>()
        }
    }

    companion object {
        const val imageFingerprintFileName = "jib-image.fingerprint"
        const val imageFingerprintTaskName = "writeJibImageFingerprint"
        const val imageFingerprintLabel = "sollecitom.image.fingerprint"
        private const val EPOCH_TIMESTAMP = "EPOCH"
        private const val EPOCH_PLUS_SECOND_TIMESTAMP = "EPOCH_PLUS_SECOND"
    }

    private fun PlatformParametersSpec.configureForOperatingSystem(currentOS: OperatingSystem, currentArchitecture: ArchitectureInternal) {

        when {
            currentOS.isMacOsX && currentArchitecture.isArm64 -> platform { appleSilicon() }
            else -> platform { linux() }
        }
    }

    private fun PlatformParameters.appleSilicon() {
        architecture = "arm64"
        os = "linux"
    }

    private fun PlatformParameters.linux() {
        architecture = "amd64"
        os = "linux"
    }

    private fun lazyList(provider: Provider<List<String>>) = object : AbstractList<String>() {
        override val size: Int get() = provider.get().size
        override fun get(index: Int): String = provider.get()[index]
    }

    private fun lazyMap(provider: Provider<Map<String, String>>) = object : MutableMap<String, String> {
        override val size: Int get() = provider.get().size
        override fun isEmpty(): Boolean = provider.get().isEmpty()
        override fun containsKey(key: String): Boolean = provider.get().containsKey(key)
        override fun containsValue(value: String): Boolean = provider.get().containsValue(value)
        override fun get(key: String): String? = provider.get()[key]
        override val keys: MutableSet<String> get() = provider.get().keys.toMutableSet()
        override val values: MutableCollection<String> get() = provider.get().values.toMutableList()
        override val entries: MutableSet<MutableMap.MutableEntry<String, String>>
            get() = provider.get().entries
                .mapTo(linkedSetOf()) { AbstractMap.SimpleEntry(it.key, it.value) }

        override fun clear(): Nothing = unsupportedMutation()
        override fun put(key: String, value: String): Nothing = unsupportedMutation()
        override fun putAll(from: Map<out String, String>): Nothing = unsupportedMutation()
        override fun remove(key: String): Nothing = unsupportedMutation()
    }

    private fun unsupportedMutation(): Nothing = throw UnsupportedOperationException("This map is read-only.")
}
