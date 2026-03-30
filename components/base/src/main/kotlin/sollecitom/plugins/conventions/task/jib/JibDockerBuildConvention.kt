package sollecitom.plugins.conventions.task.jib

import com.google.cloud.tools.jib.gradle.JibExtension
import com.google.cloud.tools.jib.gradle.JibPlugin
import com.google.cloud.tools.jib.gradle.PlatformParameters
import com.google.cloud.tools.jib.gradle.PlatformParametersSpec
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Optional
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.nativeplatform.platform.OperatingSystem
import org.gradle.nativeplatform.platform.internal.ArchitectureInternal
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import java.time.Instant

/** Convention plugin that configures Jib for building Docker images. Automatically detects the host platform (including Apple Silicon) and configures the target architecture accordingly. */
abstract class JibDockerBuildConvention : Plugin<Project> {

    override fun apply(project: Project) = with(project) {

        pluginManager.apply(JibPlugin::class)
        val settings = project.extensions.create<Extension>("jibDockerBuildConvention")

        // afterEvaluate is required here because Jib's extension uses plain String properties (not Gradle Property<T>),
        // so values must be read after consumers configure the extension
        afterEvaluate {
            extensions.configure<JibExtension> {
                container {
                    args = settings.argsValue
                    jvmFlags = settings.jvmFlagsValue
                    volumes = settings.volumesValue
                    environment = settings.environment.get()
                    user = settings.userValue
                    setFormat(settings.imageFormatValue)
                    if (!settings.reproducibleBuildValue) {
                        val buildTimestamp = Instant.now()
                        creationTime.set(buildTimestamp.toString())
                        filesModificationTime.set(buildTimestamp.toString())
                    }
                    labels.set(settings.labelsValue)
                    containerizingMode = "exploded"
                    mainClass = settings.starterClassFullyQualifiedName.get()
                }
                from {
                    image = settings.dockerBaseImage.get()
                    platforms {
                        configureForOperatingSystem(currentOperatingSystem, currentArchitecture)
                    }
                }
                to {
                    image = settings.serviceImageName.get()
                    tags = settings.tagsValue.toSet()
                }
            }
        }
    }

    private val currentOperatingSystem: OperatingSystem get() = DefaultNativePlatform.getCurrentOperatingSystem()
    private val currentArchitecture: ArchitectureInternal get() = DefaultNativePlatform.getCurrentArchitecture()

    private val Extension.reproducibleBuildValue: Boolean get() = reproducibleBuild.getOrElse(Extension.Companion.defaultReproducibleBuild)
    private val Extension.userValue: String get() = user.getOrElse(Extension.Companion.defaultUser)
    private val Extension.imageFormatValue: String get() = imageFormat.getOrElse(Extension.Companion.defaultImageFormat)
    private val Extension.tagsValue: List<String> get() = tags.getOrElse(Extension.Companion.defaultTags)
    private val Extension.argsValue: List<String> get() = args.getOrElse(Extension.Companion.defaultArgs)
    private val Extension.jvmFlagsValue: List<String> get() = jvmFlags.getOrElse(Extension.Companion.defaultJvmFlags)
    private val Extension.volumesValue: List<String> get() = volumes.getOrElse(Extension.Companion.defaultVolumes)
    private val Extension.labelsValue: Map<String, String> get() = labels.getOrElse(Extension.Companion.defaultLabels)

    /**
     * Extension for configuring Jib Docker image builds.
     *
     * Required properties: [starterClassFullyQualifiedName], [dockerBaseImage], [serviceImageName].
     * All other properties have sensible defaults (OCI format, "nobody" user, non-reproducible build).
     */
    abstract class Extension {

        /** Fully qualified name of the main class (required). */
        abstract val starterClassFullyQualifiedName: Property<String>
        /** Base Docker image to build from (required). */
        abstract val dockerBaseImage: Property<String>
        /** Target image name for the built image (required). */
        abstract val serviceImageName: Property<String>

        /** Whether to produce reproducible builds with fixed timestamps. Defaults to false. */
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

        /** Container image format. Defaults to "OCI". */
        @get:Optional
        abstract val imageFormat: Property<String>

        /** User to run the container as. Defaults to "nobody". */
        @get:Optional
        abstract val user: Property<String>

        /** Image labels. Defaults to empty. */
        @get:Optional
        abstract val labels: MapProperty<String, String>

        /** Environment variables for the container. */
        @get:Optional
        abstract val environment: MapProperty<String, String>

        internal companion object {
            const val defaultReproducibleBuild = false
            const val defaultImageFormat = "OCI"
            const val defaultUser = "nobody"
            val defaultTags = emptyList<String>()
            val defaultArgs = emptyList<String>()
            val defaultJvmFlags = emptyList<String>()
            val defaultVolumes = emptyList<String>()
            val defaultLabels = emptyMap<String, String>()
        }
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
}