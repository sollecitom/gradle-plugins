package sollecitom.plugins.conventions.task.maven.publish

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

abstract class PublishIfChangedConvention : Plugin<Project> {

    override fun apply(project: Project) = with(project) {
        check(project == rootProject) { "sollecitom.publish-if-changed-conventions must be applied to the root project." }

        tasks.register<WritePublicationStateTask>("writePublicationState") {
            val artifacts = project.rootProject.subprojects
                .filter { candidate -> candidate.pluginManager.hasPlugin("maven-publish") }
                .flatMap { candidate ->
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

            dependsOn(project.rootProject.subprojects.filter { candidate -> candidate.pluginManager.hasPlugin("maven-publish") }.map { "${it.path}:build" })
            currentVersion.set(project.version.toString())
            artifactCoordinates.set(artifacts.map { it.coordinate })
            artifactPaths.set(artifacts.map { it.buildFile.absolutePath })
            outputFile.set(layout.buildDirectory.file("publication-state/publication-state.properties"))
        }
        Unit
    }
}
