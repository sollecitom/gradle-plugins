package sollecitom.plugins.conventions.task.maven.publish

import com.vdurmont.semver4j.Semver
import java.io.File
import java.security.MessageDigest

internal data class PublishedArtifact(
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

internal data class PublicationState(
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

internal object PublicationHashGate {

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

        val targetVersion = if (changedArtifacts.isEmpty()) {
            latestPublishedVersion
        } else {
            nextPatchVersion(latestPublishedVersion, currentVersion)
        }

        return PublicationState(
            status = if (changedArtifacts.isEmpty()) PublicationState.Status.UNCHANGED else PublicationState.Status.PUBLISH_REQUIRED,
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
            val currentVersion = artifact.coordinate.substringBefore('@').split(':')[2]
            val artifactRoot = artifact.publishedFile(currentVersion, mavenLocalRepository).parentFile?.parentFile
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
        return commonVersions.maxWithOrNull(compareBy(::Semver))
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
