plugins {
    alias(libs.plugins.kotlin.jvm)
    `kotlin-dsl`
    alias(libs.plugins.com.github.ben.manes.versions)
    alias(libs.plugins.nl.littlerobots.version.catalog.update)
    id("maven-publish")
}

val projectGroup: String by properties
val currentVersion: String by properties

group = projectGroup
version = currentVersion

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.semver4j)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.nl.littlerobots.version.catalog.update)
    implementation(libs.com.github.ben.manes.versions)
    implementation(libs.jib.gradle.plugin)
}

publishing {
    publications {
        create("$name-maven", MavenPublication::class.java) {
            groupId = rootProject.group.toString()
            artifactId = project.name
            version = rootProject.version.toString()
            from(components["java"])
            logger.quiet("Created publication ${groupId}:${artifactId}:${version}")
        }
    }
}