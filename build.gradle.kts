plugins {
    alias(libs.plugins.kotlin.jvm)
    `kotlin-dsl`
    alias(libs.plugins.com.github.ben.manes.versions)
    alias(libs.plugins.nl.littlerobots.version.catalog.update)
    `maven-publish`
}

val projectGroup: String by properties
val currentVersion: String by properties

group = projectGroup
version = currentVersion

allprojects {

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

    publishing {
        publications {
            create("${project.name}-maven", MavenPublication::class.java) {
                groupId = project.rootProject.group.toString()
                artifactId = project.name
                version = project.rootProject.version.toString()
                from(project.components["java"])
                logger.quiet("Created publication ${groupId}:${artifactId}:${version}")
            }
        }
    }
}