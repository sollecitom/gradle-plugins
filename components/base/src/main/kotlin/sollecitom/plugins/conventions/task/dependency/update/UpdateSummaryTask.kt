package sollecitom.plugins.conventions.task.dependency.update

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

@DisableCachingByDefault(because = "This task reads git state and working tree files that are not declared as cacheable inputs.")
abstract class UpdateSummaryTask @Inject constructor(
    private val execOperations: ExecOperations
) : DefaultTask() {

    init {
        group = "help"
        description = "Prints a concise summary of upgrade-related changes in the working tree."
    }

    @TaskAction
    fun printSummary() {
        val changedFiles = git("diff", "--name-only", "--diff-filter=ACMR")
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()
        val gradlePropertiesSummary = if ("gradle.properties" in changedFiles) {
            summarizeKeyValueFile("gradle.properties")
        } else {
            emptyList()
        }
        val hasImagePropertySummary = gradlePropertiesSummary.any {
            it.startsWith("Java image") || it.startsWith("Java runtime image")
        }

        val summaryLines = mutableListOf<String>()

        changedFiles.forEach { file ->
            when {
                file in keyValueFiles -> {
                    val lines = if (file == "gradle.properties") gradlePropertiesSummary else summarizeKeyValueFile(file)
                    if (lines.isNotEmpty()) summaryLines += lines
                }
                file == "Dockerfile" || file.endsWith("/Dockerfile") -> {
                    if (!hasImagePropertySummary) {
                        summarizeDockerfile(file)?.let(summaryLines::add)
                    }
                }
            }
        }

        summaryLines += workspaceEventLines()

        if (summaryLines.isEmpty()) return

        summaryLines.distinct().forEach(::println)
    }

    private fun summarizeKeyValueFile(path: String): List<String> {
        val current = project.projectDir.resolve(path)
        if (!current.exists()) return emptyList()

        val previousContent = gitOrNull("show", "HEAD:$path") ?: ""
        val previousValues = parseKeyValueContent(previousContent)
        val currentValues = parseKeyValueContent(current.readText())
        val keys = (previousValues.keys + currentValues.keys).sorted()

        return keys.mapNotNull { key ->
            val previousValue = previousValues[key]
            val currentValue = currentValues[key]
            if (previousValue == currentValue) return@mapNotNull null

            when {
                path == "gradle/wrapper/gradle-wrapper.properties" && key == "distributionSha256Sum" -> null
                path == "gradle/wrapper/gradle-wrapper.properties" && key == "distributionUrl" ->
                    "Gradle: ${extractGradleVersion(previousValue)} → ${extractGradleVersion(currentValue)}"
                path == "gradle.properties" && key == "dockerBaseImageParam" ->
                    summarizeImageChange("Java image", previousValue, currentValue)
                path == "gradle.properties" && key == "dockerRuntimeBaseImageParam" ->
                    summarizeImageChange("Java runtime image", previousValue, currentValue)
                path == "gradle.properties" && key in suppressedGradleProperties -> null
                else -> "$key: ${display(previousValue)} → ${display(currentValue)}"
            }
        }
    }

    private fun summarizeDockerfile(path: String): String? {
        val current = project.projectDir.resolve(path)
        if (!current.exists()) return null

        val previousFrom = extractDockerFromLines(gitOrNull("show", "HEAD:$path").orEmpty())
        val currentFrom = extractDockerFromLines(current.readText())

        return if (currentFrom.isNotEmpty() && previousFrom != currentFrom) {
            val previousTags = previousFrom.mapNotNull(::dockerStageTag)
            val currentTags = currentFrom.mapNotNull(::dockerStageTag)

            when {
                previousTags.isEmpty() || currentTags.isEmpty() ->
                    "Docker base images changed"
                previousTags == currentTags ->
                    "Docker base digests refreshed: ${currentTags.joinToString("; ")}"
                else ->
                    "Docker base: ${previousTags.joinToString("; ")} → ${currentTags.joinToString("; ")}"
            }
        } else {
            null
        }
    }

    private fun extractDockerFromLines(content: String): List<String> =
        content.lineSequence()
            .map(String::trim)
            .filter { it.startsWith("FROM ") }
            .map { it.removePrefix("FROM ").trim() }
            .toList()

    private fun summarizeImageChange(label: String, previousValue: String?, currentValue: String?): String {
        val previousTag = imageTag(previousValue)
        val currentTag = imageTag(currentValue)
        val previousDigest = imageDigest(previousValue)
        val currentDigest = imageDigest(currentValue)

        return when {
            previousTag == null || currentTag == null -> "$label: ${display(previousValue)} → ${display(currentValue)}"
            previousTag == currentTag && previousDigest != currentDigest -> "$label digest refreshed: $currentTag"
            else -> "$label: $previousTag → $currentTag"
        }
    }

    private fun parseKeyValueContent(content: String): Map<String, String> {
        val regex = Regex("""^\s*([A-Za-z0-9_.-]+)\s*=\s*"?([^"]*)"?\s*$""")
        return buildMap {
            content.lineSequence()
                .map(String::trim)
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .forEach { line ->
                    val match = regex.matchEntire(line) ?: return@forEach
                    put(match.groupValues[1], match.groupValues[2])
                }
        }
    }

    private fun workspaceEventLines(): List<String> {
        val path = System.getenv("WORKSPACE_UPDATE_EVENTS_FILE")?.takeIf(String::isNotBlank) ?: return emptyList()
        val file = File(path)
        if (!file.exists()) return emptyList()

        // Preserve emission order so workspace summaries stay stable across runs.
        return file.readLines()
            .map(String::trim)
            .filter(String::isNotEmpty)
    }

    private fun imageTag(value: String?): String? {
        val raw = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val withoutDigest = raw.substringBefore('@')
        val colonIndex = withoutDigest.lastIndexOf(':')
        if (colonIndex < 0 || colonIndex == withoutDigest.lastIndexOf('/')) return null
        return withoutDigest.substring(colonIndex + 1)
    }

    private fun imageDigest(value: String?): String? =
        value?.substringAfter('@', "")?.takeIf(String::isNotBlank)

    private fun dockerStageTag(stage: String): String? =
        imageTag(stage.substringBefore(" AS ").trim())

    private fun extractGradleVersion(value: String?): String =
        value
            ?.let { Regex("""gradle-([0-9][A-Za-z0-9.+-]*)-(?:bin|all)\.zip""").find(it)?.groupValues?.get(1) }
            ?: display(value)

    private fun display(value: String?): String = value?.takeIf(String::isNotBlank) ?: "(missing)"

    private fun git(vararg args: String): String =
        gitOrNull(*args) ?: error("Failed to run git ${args.joinToString(" ")} in ${project.projectDir}")

    private fun gitOrNull(vararg args: String): String? {
        val stdout = ByteArrayOutputStream()
        val result = execOperations.exec {
            workingDir(project.projectDir)
            commandLine("git", *args)
            standardOutput = stdout
            errorOutput = ByteArrayOutputStream()
            isIgnoreExitValue = true
        }
        return stdout.toString().takeIf { result.exitValue == 0 }
    }

    private companion object {
        val keyValueFiles = setOf(
            "gradle/libs.versions.toml",
            "container-versions.properties",
            "gradle.properties",
            "gradle/wrapper/gradle-wrapper.properties",
        )
        val suppressedGradleProperties = setOf(
            "dockerBaseImageRepository",
            "dockerBaseImageVariant",
            "dockerBaseImageMajor",
            "dockerRuntimeBaseImageVariant",
        )
    }
}
