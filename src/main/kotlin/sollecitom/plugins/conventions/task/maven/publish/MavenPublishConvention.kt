package sollecitom.plugins.conventions.task.maven.publish

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByName
import sollecitom.plugins.RepositoryConfiguration

abstract class MavenPublishConvention : Plugin<Project> {

    override fun apply(project: Project) = with(project) {

        val sourceSets = properties["sourceSets"] as SourceSetContainer
        val sourceSet = sourceSets.getByName("main")
        val sourcesJar = tasks.register("source-jar", Jar::class.java) {
            archiveClassifier.set("sources")
            from(sourceSet.allSource)
        }

        pluginManager.apply(MavenPublishPlugin::class)

        afterEvaluate {
            extensions.configure<PublishingExtension> {
                repositories {
                    RepositoryConfiguration.Publications.apply(this, project)
                }
                publications {
                    create("${project.name}-maven", MavenPublication::class.java) {
                        groupId = project.group.toString()
                        artifactId = project.name
                        version = project.version.toString()
                        from(components["java"])
                        artifact(sourcesJar)
                        logger.quiet("Created publication ${groupId}:${artifactId}:${version}")
                    }
                }
            }
        }
    }
}