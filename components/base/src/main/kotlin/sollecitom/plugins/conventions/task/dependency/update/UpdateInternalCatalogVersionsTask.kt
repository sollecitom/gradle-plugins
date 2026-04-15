package sollecitom.plugins.conventions.task.dependency.update

import nl.littlerobots.vcu.VersionCatalogParser
import nl.littlerobots.vcu.VersionCatalogWriter
import nl.littlerobots.vcu.model.Plugin
import nl.littlerobots.vcu.model.VersionCatalog
import nl.littlerobots.vcu.model.VersionDefinition
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "This task mutates the local version catalog in place based on local Maven metadata.")
abstract class UpdateInternalCatalogVersionsTask : DefaultTask() {

    @get:Internal
    abstract val catalogFile: RegularFileProperty

    @get:Input
    abstract val internalGroups: ListProperty<String>

    init {
        group = "version catalog update"
        description = "Updates internal dependency versions from the local Maven repository."
    }

    @TaskAction
    fun updateCatalog() {
        val file = catalogFile.asFile.get()
        if (!file.exists()) {
            logger.info("Skipping internal dependency update because ${file.path} does not exist.")
            return
        }

        val configuredGroups = internalGroups.get()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()

        if (configuredGroups.isEmpty()) {
            logger.info("Skipping internal dependency update because no internal groups are configured.")
            return
        }

        val currentCatalog = VersionCatalogParser().parse(file.inputStream())
        val referencedCoordinates = currentCatalog.internalVersionReferences(configuredGroups)
        if (referencedCoordinates.isEmpty()) {
            logger.info("No internal dependency references were found in ${file.path}.")
            return
        }

        val mavenLocalRepository = File(System.getProperty("user.home"), ".m2/repository")
        val updatedVersions = currentCatalog.versions.toMutableMap()
        val appliedUpdates = mutableListOf<String>()

        referencedCoordinates.toSortedMap().forEach { (versionRef, coordinates) ->
            val currentVersion = updatedVersions[versionRef] as? VersionDefinition.Simple ?: return@forEach
            val availableVersions = coordinates.mapNotNull { coordinate ->
                coordinate.latestPublishedVersion(mavenLocalRepository)
            }.distinct()

            if (availableVersions.isEmpty()) {
                return@forEach
            }

            require(availableVersions.size == 1) {
                buildString {
                    append("Inconsistent published versions for internal version ref '$versionRef': ")
                    append(
                        coordinates.joinToString { coordinate ->
                            val resolvedVersion = coordinate.latestPublishedVersion(mavenLocalRepository) ?: "(missing)"
                            "${coordinate.group}:${coordinate.artifactId} -> $resolvedVersion"
                        }
                    )
                }
            }

            val targetVersion = availableVersions.single()
            if (DependencyVersion(targetVersion) > DependencyVersion(currentVersion.version)) {
                updatedVersions[versionRef] = VersionDefinition.Simple(targetVersion)
                appliedUpdates += "$versionRef: ${currentVersion.version} -> $targetVersion"
            }
        }

        if (appliedUpdates.isEmpty()) {
            logger.lifecycle("No internal dependency updates available.")
            return
        }

        VersionCatalogWriter().write(currentCatalog.copy(versions = updatedVersions), file.writer())
        appliedUpdates.forEach { logger.lifecycle(it) }
    }
}

private fun VersionCatalog.internalVersionReferences(
    internalGroups: List<String>
): Map<String, Set<MavenCoordinate>> {
    val references = linkedMapOf<String, MutableSet<MavenCoordinate>>()

    libraries.values.forEach { library ->
        val versionRef = (library.version as? VersionDefinition.Reference)?.ref ?: return@forEach
        val group = library.module.substringBefore(':', "")
        val artifactId = library.module.substringAfter(':', "")
        if (!matchesInternalIdentifier(group, internalGroups) || artifactId.isEmpty()) return@forEach
        references.getOrPut(versionRef) { linkedSetOf() }.add(MavenCoordinate(group, artifactId))
    }

    plugins.values.forEach { plugin ->
        val versionRef = (plugin.version as? VersionDefinition.Reference)?.ref ?: return@forEach
        if (!matchesInternalIdentifier(plugin.id, internalGroups)) return@forEach
        references.getOrPut(versionRef) { linkedSetOf() }.add(plugin.markerCoordinate())
    }

    return references
}

private fun Plugin.markerCoordinate(): MavenCoordinate =
    MavenCoordinate(group = id, artifactId = "$id.gradle.plugin")

private fun matchesInternalIdentifier(
    value: String,
    internalGroups: List<String>
): Boolean = internalGroups.any { group ->
    value == group || value.startsWith("$group.") || value.startsWith("$group-")
}

private data class MavenCoordinate(
    val group: String,
    val artifactId: String
) {
    fun latestPublishedVersion(mavenLocalRepository: File): String? {
        val groupPath = group.replace('.', File.separatorChar)
        val metadataFile = mavenLocalRepository.resolve("$groupPath/$artifactId/maven-metadata-local.xml")
        if (!metadataFile.exists()) return null

        val xml = metadataFile.readText()
        val preferredCandidates = listOf(
            xml.singleTagValue("release"),
            xml.singleTagValue("latest"),
        )
            .filterNotNull()
            .filterNot { it.endsWith("-SNAPSHOT") }

        val fallbackCandidates = Regex("<version>([^<]+)</version>")
            .findAll(xml)
            .map { it.groupValues[1] }
            .filterNot { it.endsWith("-SNAPSHOT") }
            .toList()

        return (preferredCandidates + fallbackCandidates)
            .distinct()
            .maxWithOrNull { left, right -> DependencyVersion(left).compareTo(DependencyVersion(right)) }
    }
}

private fun String.singleTagValue(tag: String): String? =
    Regex("<$tag>([^<]+)</$tag>")
        .find(this)
        ?.groupValues
        ?.get(1)
