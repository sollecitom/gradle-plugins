package sollecitom.buildsrc.publish

import com.vdurmont.semver4j.Semver
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.security.MessageDigest
import java.util.Properties

@DisableCachingByDefault(because = "This task inspects built artifacts and Maven Local to decide whether a new version should be published.")
abstract class WritePublicationStateTask : DefaultTask() {

    init {
        group = "publishing"
        description = "Writes the local publication state derived from built artifacts and Maven Local."
    }

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:Input
    abstract val artifactCoordinates: ListProperty<String>

    @get:Input
    abstract val artifactPaths: ListProperty<String>

    @get:Input
    abstract val currentVersion: Property<String>

    @TaskAction
    fun writeState() {
        val artifacts = artifactCoordinates.get().zip(artifactPaths.get()).map { (coordinate, path) ->
            PublishedArtifact(
                coordinate = coordinate,
                buildFile = File(path),
            )
        }.filter { artifact -> artifact.buildFile.exists() }

        val state = PublicationHashGate.inspect(
            currentVersion = currentVersion.get(),
            artifacts = artifacts,
        )

        val output = outputFile.get().asFile
        output.parentFile.mkdirs()

        Properties().apply {
            setProperty("status", state.status.name)
            setProperty("currentVersion", state.currentVersion)
            state.latestPublishedVersion?.let { setProperty("latestPublishedVersion", it) }
            setProperty("targetVersion", state.targetVersion)
            setProperty("changedArtifacts", state.changedArtifacts.joinToString(","))
        }.also { properties ->
            output.outputStream().use { stream -> properties.store(stream, null) }
        }
    }

}

fun Project.publishableProjects(): List<Project> =
    rootProject.subprojects.filter { candidate -> candidate.pluginManager.hasPlugin("maven-publish") }

fun Project.publishableArtifacts(): List<PublishedArtifact> =
    publishableProjects().flatMap { candidate ->
        val version = candidate.version.toString()
        val libsDir = candidate.layout.buildDirectory.dir("libs").get().asFile
        listOf(null, "sources", "javadoc").map { classifier ->
            val classifierSuffix = classifier?.let { "-$it" }.orEmpty()
            PublishedArtifact(
                coordinate = buildString {
                    append(candidate.group.toString())
                    append(':')
                    append(candidate.name)
                    append(':')
                    append(version)
                    classifier?.let {
                        append(':')
                        append(it)
                    }
                    append("@jar")
                },
                buildFile = libsDir.resolve("${candidate.name}-$version$classifierSuffix.jar"),
            )
        }
    }

data class PublishedArtifact(
    val coordinate: String,
    val buildFile: File,
) {
    private val coordinates = coordinate.substringBefore('@').split(':')
    private val extension = coordinate.substringAfter('@')

    fun publishedFile(versionOverride: String, mavenLocalRepository: File): File {
        val group = coordinates[0]
        val artifactId = coordinates[1]
        val classifier = coordinates.getOrNull(3)
        val groupPath = group.replace('.', '/')
        val classifierSuffix = classifier?.let { "-$it" }.orEmpty()
        return mavenLocalRepository.resolve("$groupPath/$artifactId/$versionOverride/$artifactId-$versionOverride$classifierSuffix.$extension")
    }
}

data class PublicationState(
    val status: Status,
    val currentVersion: String,
    val latestPublishedVersion: String?,
    val targetVersion: String,
    val changedArtifacts: List<String>,
) {
    enum class Status {
        UNCHANGED,
        PUBLISH_REQUIRED,
    }
}

object PublicationHashGate {

    fun inspect(
        currentVersion: String,
        artifacts: List<PublishedArtifact>,
        mavenLocalRepository: File = File(System.getProperty("user.home"), ".m2/repository"),
    ): PublicationState {

        require(artifacts.isNotEmpty()) { "No publishable artifacts were found." }

        val latestPublishedVersion = latestCommonPublishedVersion(artifacts, mavenLocalRepository)
        if (latestPublishedVersion == null) {
            return PublicationState(
                status = PublicationState.Status.PUBLISH_REQUIRED,
                currentVersion = currentVersion,
                latestPublishedVersion = null,
                targetVersion = normalizeInitialVersion(currentVersion),
                changedArtifacts = artifacts.map(PublishedArtifact::coordinate),
            )
        }

        val changedArtifacts = artifacts.mapNotNull { artifact ->
            val publishedFile = artifact.publishedFile(latestPublishedVersion, mavenLocalRepository)
            when {
                !publishedFile.exists() -> artifact.coordinate
                sha256(artifact.buildFile) != sha256(publishedFile) -> artifact.coordinate
                else -> null
            }
        }

        val publishCurrentVersion = changedArtifacts.isEmpty() &&
            isStableSemver(currentVersion) &&
            Semver(currentVersion, Semver.SemverType.STRICT) > Semver(latestPublishedVersion, Semver.SemverType.STRICT)

        val targetVersion = when {
            publishCurrentVersion -> currentVersion
            changedArtifacts.isEmpty() -> latestPublishedVersion
            else -> nextPatchVersion(latestPublishedVersion, currentVersion)
        }

        return PublicationState(
            status = if (changedArtifacts.isEmpty() && !publishCurrentVersion) PublicationState.Status.UNCHANGED else PublicationState.Status.PUBLISH_REQUIRED,
            currentVersion = currentVersion,
            latestPublishedVersion = latestPublishedVersion,
            targetVersion = targetVersion,
            changedArtifacts = changedArtifacts,
        )
    }

    private fun latestCommonPublishedVersion(
        artifacts: List<PublishedArtifact>,
        mavenLocalRepository: File,
    ): String? {

        val versionSets = artifacts.map { artifact ->
            val artifactRoot = artifact.publishedFile(artifact.coordinate.substringBefore('@').split(':')[2], mavenLocalRepository).parentFile?.parentFile
                ?: return null
            artifactRoot.listFiles()
                .orEmpty()
                .asSequence()
                .filter(File::isDirectory)
                .map(File::getName)
                .filter(::isStableSemver)
                .toSet()
        }

        val commonVersions = versionSets.reduceOrNull { acc, versions -> acc intersect versions }.orEmpty()
        return commonVersions.maxWithOrNull(compareBy { Semver(it, Semver.SemverType.STRICT) })
    }

    private fun nextPatchVersion(latestPublishedVersion: String, currentVersion: String): String {
        val baseVersion = sequenceOf(currentVersion, latestPublishedVersion)
            .filter(::isStableSemver)
            .map { Semver(it, Semver.SemverType.STRICT) }
            .maxOrNull()
            ?: return normalizeInitialVersion(currentVersion)

        return baseVersion.nextPatch().value
    }

    private fun normalizeInitialVersion(currentVersion: String): String =
        currentVersion.takeIf(::isStableSemver) ?: "1.0.0"

    private fun isStableSemver(value: String): Boolean =
        runCatching { Semver(value, Semver.SemverType.STRICT) }.isSuccess

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val bytesRead = input.read(buffer)
                if (bytesRead < 0) break
                if (bytesRead > 0) digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
