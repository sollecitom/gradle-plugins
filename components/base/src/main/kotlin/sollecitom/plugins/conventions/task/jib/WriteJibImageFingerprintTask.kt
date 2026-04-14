package sollecitom.plugins.conventions.task.jib

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.security.MessageDigest

@CacheableTask
abstract class WriteJibImageFingerprintTask : DefaultTask() {

    @get:Classpath
    abstract val runtimeClasspath: ConfigurableFileCollection

    @get:OutputFile
    abstract val fingerprintFile: RegularFileProperty

    @get:Input
    abstract val starterClassFullyQualifiedName: Property<String>

    @get:Input
    abstract val dockerBaseImage: Property<String>

    @get:Input
    abstract val serviceImageName: Property<String>

    @get:Input
    abstract val reproducibleBuild: Property<Boolean>

    @get:Input
    abstract val volumes: ListProperty<String>

    @get:Input
    abstract val jvmFlags: ListProperty<String>

    @get:Input
    abstract val args: ListProperty<String>

    @get:Input
    abstract val tags: ListProperty<String>

    @get:Input
    abstract val imageFormat: Property<String>

    @get:Input
    abstract val user: Property<String>

    @get:Input
    abstract val labels: MapProperty<String, String>

    @get:Input
    abstract val environment: MapProperty<String, String>

    @TaskAction
    fun writeFingerprint() {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.updateLine("starterClass=${starterClassFullyQualifiedName.get()}")
        digest.updateLine("dockerBaseImage=${dockerBaseImage.get()}")
        digest.updateLine("serviceImageName=${serviceImageName.get()}")
        digest.updateLine("reproducibleBuild=${reproducibleBuild.getOrElse(JibDockerBuildConvention.Extension.defaultReproducibleBuild)}")
        volumes.getOrElse(emptyList()).sorted().forEach { digest.updateLine("volume=$it") }
        jvmFlags.getOrElse(emptyList()).forEach { digest.updateLine("jvmFlag=$it") }
        args.getOrElse(emptyList()).forEach { digest.updateLine("arg=$it") }
        tags.getOrElse(emptyList()).sorted().forEach { digest.updateLine("tag=$it") }
        digest.updateLine("imageFormat=${imageFormat.getOrElse(JibDockerBuildConvention.Extension.defaultImageFormat)}")
        digest.updateLine("user=${user.getOrElse(JibDockerBuildConvention.Extension.defaultUser)}")
        labels.getOrElse(emptyMap()).toSortedMap().forEach { (key, value) -> digest.updateLine("label=$key=$value") }
        environment.getOrElse(emptyMap()).toSortedMap().forEach { (key, value) -> digest.updateLine("env=$key=$value") }
        runtimeClasspath.files.sortedBy(File::getAbsolutePath).forEach { file ->
            digest.updateFile(file)
        }

        val fingerprint = digest.digest().joinToString(separator = "") { "%02x".format(it) }
        fingerprintFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText("$fingerprint\n")
        }
    }

    private fun MessageDigest.updateFile(file: File) {
        updateLine("path=${file.absolutePath}")
        if (!file.exists()) {
            updateLine("missing")
            return
        }
        if (file.isDirectory) {
            file.walkTopDown()
                .filter(File::isFile)
                .sortedBy(File::getAbsolutePath)
                .forEach { nestedFile ->
                    updateLine("file=${nestedFile.relativeTo(file).invariantSeparatorsPath}")
                    update(nestedFile.readBytes())
                }
            return
        }
        update(file.readBytes())
    }

    private fun MessageDigest.updateLine(value: String) {
        update(value.toByteArray(Charsets.UTF_8))
        update(byteArrayOf('\n'.code.toByte()))
    }
}
