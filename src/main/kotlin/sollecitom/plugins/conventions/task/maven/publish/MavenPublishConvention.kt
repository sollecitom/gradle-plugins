package sollecitom.plugins.conventions.task.maven.publish

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.get
import sollecitom.plugins.RepositoryConfiguration

abstract class MavenPublishConvention : Plugin<Project> {

    override fun apply(project: Project) = with(project) {

        pluginManager.apply(MavenPublishPlugin::class)
        pluginManager.apply(JavaPlugin::class)

        afterEvaluate {
            extensions.configure<JavaPluginExtension> {
                withSourcesJar()
                withJavadocJar()
            }

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
                        logger.quiet("Created publication ${groupId}:${artifactId}:${version}")
                    }
                }
            }
        }
    }
}