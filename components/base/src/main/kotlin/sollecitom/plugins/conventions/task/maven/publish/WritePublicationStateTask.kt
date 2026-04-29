package sollecitom.plugins.conventions.task.maven.publish

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.util.Properties

@DisableCachingByDefault(because = "This task inspects built artifacts and the local Maven repository to decide whether publishing is required.")
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

    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val trackedStateFile: RegularFileProperty

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
            trackedStateFile = trackedStateFile.asFile.orNull,
        )

        val output = outputFile.get().asFile
        output.parentFile.mkdirs()

        Properties().apply {
            setProperty("status", state.status.name)
            setProperty("currentVersion", state.currentVersion)
            state.latestPublishedVersion?.let { setProperty("latestPublishedVersion", it) }
            setProperty("targetVersion", state.targetVersion)
            setProperty("changedArtifacts", state.changedArtifacts.joinToString(","))
            setProperty("trackedPublishedVersion", state.trackedState.publishedVersion)
            setProperty("trackedArtifactCount", state.trackedState.artifactHashes.size.toString())
            state.trackedState.artifactHashes.entries.sortedBy { it.key }.forEachIndexed { index, entry ->
                val prefix = "trackedArtifact.${index + 1}"
                setProperty("$prefix.identity", entry.key)
                setProperty("$prefix.sha256", entry.value)
            }
        }.also { properties ->
            output.outputStream().use { stream -> properties.store(stream, null) }
        }
    }

}
