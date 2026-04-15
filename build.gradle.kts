import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.kotlin.dsl.register
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import sollecitom.buildsrc.publish.publishableArtifacts
import sollecitom.buildsrc.publish.publishableProjects
import sollecitom.buildsrc.publish.WritePublicationStateTask
import java.io.ByteArrayOutputStream
import javax.inject.Inject

plugins {
    alias(libs.plugins.kotlin.jvm)
    `kotlin-dsl`
    alias(libs.plugins.nl.littlerobots.version.catalog.update)
    `maven-publish`
}

val projectGroup: String by properties
val currentVersion: String by properties

group = projectGroup
version = currentVersion

allprojects {

    group = projectGroup
    version = currentVersion

    repositories {
        mavenCentral()
        gradlePluginPortal()
    }

    apply<JavaLibraryPlugin>()
    apply<KotlinDslPlugin>()
}

subprojects {
    apply<MavenPublishPlugin>()

    configure<JavaPluginExtension> {
        withSourcesJar()
        withJavadocJar()
    }

    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }

    tasks.withType<Javadoc>().configureEach {
        (options as? StandardJavadocDocletOptions)?.addBooleanOption("notimestamp", true)
    }

    publishing {
        publications {
            create("${project.name}-maven", MavenPublication::class.java) {
                groupId = project.rootProject.group.toString()
                artifactId = project.name
                version = project.rootProject.version.toString()
                from(project.components["java"])
            }
        }
    }

    val coordinates = "${project.rootProject.group}:${project.name}:${project.rootProject.version}"
    tasks.named("publishToMavenLocal") {
        doLast {
            println("Published $coordinates")
        }
    }
}

tasks.register<UpdateSummaryTask>("updateSummary")
tasks.register<WritePublicationStateTask>("writePublicationState") {
    dependsOn(publishableProjects().map { "${it.path}:build" })
    currentVersion.set(project.version.toString())
    artifactCoordinates.set(publishableArtifacts().map { it.coordinate })
    artifactPaths.set(publishableArtifacts().map { it.buildFile.absolutePath })
    outputFile.set(layout.buildDirectory.file("publication-state/publication-state.properties"))
}

@DisableCachingByDefault(because = "This task reads git state and working tree files that are not declared as cacheable inputs.")
abstract class UpdateSummaryTask @Inject constructor(
    private val execOperations: ExecOperations
) : DefaultTask() {

    init {
        group = "help"
        description = "Prints a summary of upgrade-related changes in the working tree."
    }

    @TaskAction
    fun printSummary() {
        val changedFiles = git("diff", "--name-only", "--diff-filter=ACMR")
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()

        if (changedFiles.isEmpty()) return

        val summaryLines = mutableListOf<String>()

        changedFiles.forEach { file ->
            when {
                file in keyValueFiles -> {
                    val lines = summarizeKeyValueFile(file)
                    if (lines.isNotEmpty()) summaryLines += lines else summaryLines += "changed: $file"
                }
                file == "Dockerfile" || file.endsWith("/Dockerfile") -> {
                    summaryLines += summarizeDockerfile(file) ?: "changed: $file"
                }
                file.endsWith("/Plugins.kt") -> {
                    summaryLines += summarizeRegexChange(file, Regex("""VERSION_(\d+)"""), "Java toolchain")
                        ?: "changed: $file"
                }
                file.endsWith("/KotlinTaskConventions.kt") -> {
                    summaryLines += summarizeRegexChange(file, Regex("""JVM_(\d+)"""), "Kotlin JVM target")
                        ?: "changed: $file"
                }
                else -> summaryLines += "changed: $file"
            }
        }

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
                    "Java image: ${display(previousValue)} → ${display(currentValue)}"
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
            val previousDisplay = previousFrom.takeIf { it.isNotEmpty() }?.joinToString("; ")
            "Docker base: ${display(previousDisplay)} → ${currentFrom.joinToString("; ")}"
        } else {
            null
        }
    }

    private fun summarizeRegexChange(path: String, regex: Regex, label: String): String? {
        val current = project.projectDir.resolve(path)
        if (!current.exists()) return null

        val previousValue = regex.find(gitOrNull("show", "HEAD:$path").orEmpty())?.groupValues?.get(1)
        val currentValue = regex.find(current.readText())?.groupValues?.get(1)

        return if (previousValue != null && currentValue != null && previousValue != currentValue) {
            "$label: $previousValue → $currentValue"
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
    }
}
