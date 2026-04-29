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
    val identityKey: String = buildString {
        append(coordinates[0])
        append(':')
        append(coordinates[1])
        coordinates.getOrNull(3)?.let {
            append(':')
            append(it)
        }
        append('@')
        append(extension)
    }

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
    val trackedState: TrackedPublicationState,
) {
    enum class Status {
        UNCHANGED,
        LOCAL_PUBLISH_REQUIRED,
        PUBLISH_REQUIRED,
    }
}

internal object PublicationHashGate {

    fun inspect(
        currentVersion: String,
        artifacts: List<PublishedArtifact>,
        trackedStateFile: File? = null,
        mavenLocalRepository: File = File(System.getProperty("user.home"), ".m2/repository"),
    ): PublicationState {

        require(artifacts.isNotEmpty()) { "No publishable artifacts were found." }

        val currentArtifactHashes = artifacts.associate { artifact ->
            artifact.identityKey to sha256(artifact.buildFile)
        }.toSortedMap()
        val trackedState = TrackedPublicationState.load(trackedStateFile)

        if (trackedState != null) {
            val changedArtifacts = currentArtifactHashes.keys
                .union(trackedState.artifactHashes.keys)
                .sorted()
                .mapNotNull { identity ->
                    if (currentArtifactHashes[identity] != trackedState.artifactHashes[identity]) identity else null
                }

            val targetVersion = if (changedArtifacts.isEmpty()) trackedState.publishedVersion else nextPatchVersion(trackedState.publishedVersion, currentVersion)
            val status = when {
                changedArtifacts.isNotEmpty() -> PublicationState.Status.PUBLISH_REQUIRED
                !artifactsAvailableLocally(artifacts, targetVersion, mavenLocalRepository) -> PublicationState.Status.LOCAL_PUBLISH_REQUIRED
                else -> PublicationState.Status.UNCHANGED
            }

            return PublicationState(
                status = status,
                currentVersion = currentVersion,
                latestPublishedVersion = trackedState.publishedVersion,
                targetVersion = targetVersion,
                changedArtifacts = changedArtifacts,
                trackedState = TrackedPublicationState(
                    publishedVersion = targetVersion,
                    artifactHashes = currentArtifactHashes,
                ),
            )
        }

        val normalizedCurrentVersion = normalizeInitialVersion(currentVersion)
        val currentVersionArtifactsChanged = artifacts.mapNotNull { artifact ->
            val publishedFile = artifact.publishedFile(normalizedCurrentVersion, mavenLocalRepository)
            when {
                !publishedFile.exists() -> artifact.identityKey
                sha256(artifact.buildFile) != sha256(publishedFile) -> artifact.identityKey
                else -> null
            }
        }

        if (currentVersionArtifactsChanged.isEmpty()) {
            return PublicationState(
                status = PublicationState.Status.UNCHANGED,
                currentVersion = currentVersion,
                latestPublishedVersion = normalizedCurrentVersion,
                targetVersion = normalizedCurrentVersion,
                changedArtifacts = emptyList(),
                trackedState = TrackedPublicationState(
                    publishedVersion = normalizedCurrentVersion,
                    artifactHashes = currentArtifactHashes,
                ),
            )
        }

        val latestPublishedVersion = latestCommonPublishedVersion(artifacts, mavenLocalRepository)
        if (latestPublishedVersion == null) {
            return PublicationState(
                status = PublicationState.Status.PUBLISH_REQUIRED,
                currentVersion = currentVersion,
                latestPublishedVersion = null,
                targetVersion = normalizedCurrentVersion,
                changedArtifacts = artifacts.map(PublishedArtifact::identityKey),
                trackedState = TrackedPublicationState(
                    publishedVersion = normalizedCurrentVersion,
                    artifactHashes = currentArtifactHashes,
                ),
            )
        }

        val changedArtifacts = artifacts.mapNotNull { artifact ->
            val publishedFile = artifact.publishedFile(latestPublishedVersion, mavenLocalRepository)
            when {
                !publishedFile.exists() -> artifact.identityKey
                sha256(artifact.buildFile) != sha256(publishedFile) -> artifact.identityKey
                else -> null
            }
        }
        val targetVersion = if (changedArtifacts.isEmpty()) latestPublishedVersion else nextPatchVersion(latestPublishedVersion, currentVersion)

        return PublicationState(
            status = if (changedArtifacts.isEmpty()) PublicationState.Status.UNCHANGED else PublicationState.Status.PUBLISH_REQUIRED,
            currentVersion = currentVersion,
            latestPublishedVersion = latestPublishedVersion,
            targetVersion = targetVersion,
            changedArtifacts = changedArtifacts,
            trackedState = TrackedPublicationState(
                publishedVersion = targetVersion,
                artifactHashes = currentArtifactHashes,
            ),
        )
    }

    private fun artifactsAvailableLocally(
        artifacts: List<PublishedArtifact>,
        version: String,
        mavenLocalRepository: File,
    ): Boolean = artifacts.all { artifact ->
        artifact.publishedFile(version, mavenLocalRepository).exists()
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

internal data class TrackedPublicationState(
    val publishedVersion: String,
    val artifactHashes: Map<String, String>,
) {
    companion object {
        fun load(file: File?): TrackedPublicationState? {
            if (file == null || !file.exists()) return null

            val properties = java.util.Properties().apply {
                file.inputStream().use(::load)
            }
            val publishedVersion = properties.getProperty("publishedVersion")?.takeIf { it.isNotBlank() } ?: return null
            val artifactCount = properties.getProperty("artifactCount")?.toIntOrNull() ?: 0
            val artifactHashes = buildMap {
                for (index in 1..artifactCount) {
                    val prefix = "artifact.$index"
                    val identity = properties.getProperty("$prefix.identity")?.takeIf { it.isNotBlank() } ?: continue
                    val hash = properties.getProperty("$prefix.sha256")?.takeIf { it.isNotBlank() } ?: continue
                    put(identity, hash)
                }
            }.toSortedMap()

            return TrackedPublicationState(
                publishedVersion = publishedVersion,
                artifactHashes = artifactHashes,
            )
        }
    }
}
