package sollecitom.plugins.conventions.task.maven.publish

import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

internal fun Project.registerWritePublicationState(publicationNameOf: (Project) -> String) {

    tasks.register<WritePublicationStateTask>("writePublicationState") {
        val publishableProjects = project.rootProject.subprojects.filter { candidate -> candidate.pluginManager.hasPlugin("maven-publish") }
        val artifacts = publishableProjects.flatMap { candidate -> candidate.publishedArtifacts(publicationNameOf(candidate)) }

        dependsOn(
            publishableProjects.flatMap { candidate ->
                val publication = publicationNameOf(candidate).replaceFirstChar { it.uppercase() }
                listOf(
                    "${candidate.path}:jar",
                    "${candidate.path}:sourcesJar",
                    "${candidate.path}:javadocJar",
                    "${candidate.path}:generatePomFileFor${publication}Publication",
                    "${candidate.path}:generateMetadataFileFor${publication}Publication",
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
}

private fun Project.publishedArtifacts(publicationName: String): List<PublishedArtifact> {

    val version = version.toString()
    val libsDir = layout.buildDirectory.dir("libs").get().asFile
    val publicationDirectory = layout.buildDirectory.dir("publications/$publicationName").get().asFile

    val jars = listOf(null, "sources", "javadoc").map { classifier ->
        val classifierSuffix = classifier?.let { "-$it" }.orEmpty()
        PublishedArtifact(
            coordinate = buildString {
                append(group.toString())
                append(':')
                append(name)
                append(':')
                append(version)
                classifier?.let {
                    append(':')
                    append(it)
                }
                append("@jar")
            },
            buildFile = libsDir.resolve("$name-$version$classifierSuffix.jar"),
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
            coordinate = "$group:$name:$version@$extension",
            buildFile = file,
        )
    }

    return jars + metadata
}
