package sollecitom.plugins.conventions.task.maven.publish

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

abstract class PublishIfChangedConvention : Plugin<Project> {

    override fun apply(project: Project) = with(project) {
        check(project == rootProject) { "sollecitom.publish-if-changed-conventions must be applied to the root project." }

        tasks.register<WritePublicationStateTask>("writePublicationState") {
            val publishableProjects = project.rootProject.subprojects.filter { candidate -> candidate.pluginManager.hasPlugin("maven-publish") }

            val artifacts = publishableProjects.flatMap { candidate ->
                val version = candidate.version.toString()
                val libsDir = candidate.layout.buildDirectory.dir("libs").get().asFile
                val publicationDirectory = candidate.layout.buildDirectory.dir("publications/${candidate.publicationName}").get().asFile

                val jars = listOf(null, "sources", "javadoc").map { classifier ->
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

                // The POM and the Gradle module metadata are where dependency versions live. A dependency-only
                // upgrade leaves the bytecode untouched, so tracking jars alone reports "unchanged" and the
                // upgrade is never republished — consumers keep resolving the previously published versions.
                // Both files are reproducible, so this does not cause spurious republishing. The module metadata
                // records the Gradle version, so a wrapper upgrade legitimately republishes every producer.
                val metadata = listOf(
                    "pom" to publicationDirectory.resolve("pom-default.xml"),
                    "module" to publicationDirectory.resolve("module.json"),
                ).map { (extension, file) ->
                    PublishedArtifact(
                        coordinate = "${candidate.group}:${candidate.name}:$version@$extension",
                        buildFile = file,
                    )
                }

                jars + metadata
            }

            dependsOn(
                publishableProjects.flatMap { candidate ->
                    listOf(
                        "${candidate.path}:jar",
                        "${candidate.path}:sourcesJar",
                        "${candidate.path}:javadocJar",
                        "${candidate.path}:generatePomFileFor${candidate.capitalizedPublicationName}Publication",
                        "${candidate.path}:generateMetadataFileFor${candidate.capitalizedPublicationName}Publication",
                    )
                }
            )
            currentVersion.set(project.version.toString())
            artifactCoordinates.set(artifacts.map { it.coordinate })
            artifactPaths.set(artifacts.map { it.buildFile.absolutePath })
            artifactFiles.setFrom(artifacts.map { it.buildFile })
            val trackedState = layout.projectDirectory.file("publication-state.properties")
            if (trackedState.asFile.exists()) {
                trackedStateFile.set(trackedState)
            }
            outputFile.set(layout.buildDirectory.file("publication-state/publication-state.properties"))
        }
        Unit
    }
}

/** Mirrors the publication [MavenPublishConvention] creates; that plugin owns the naming. */
private val Project.publicationName: String get() = "$name-maven"

/** The form Gradle uses when it derives `generatePomFileFor<Publication>Publication` task names. */
private val Project.capitalizedPublicationName: String get() = publicationName.replaceFirstChar { it.uppercase() }
