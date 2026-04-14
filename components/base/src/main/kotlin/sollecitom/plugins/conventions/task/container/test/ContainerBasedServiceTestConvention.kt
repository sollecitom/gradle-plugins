package sollecitom.plugins.conventions.task.container.test

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Property
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.DependencyHandlerScope
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import sollecitom.plugins.conventions.task.jib.JibDockerBuildConvention

// TODO rename to ContainerizedServiceTestConvention
/** Convention plugin that creates a `containerBasedServiceTest` source set and task for running integration tests against a Docker container built by Jib. */
abstract class ContainerBasedServiceTestConvention : Plugin<Project> {

    override fun apply(project: Project) = with(project) {

        val sourceSet = extensions.getByType<JavaPluginExtension>().sourceSets.create("containerBasedServiceTest")
        val extension = project.extensions.create<Extension>("containerBasedServiceTest")
        val imageFingerprint = providers.provider {
            project.project(":${extension.starterModuleName.get()}")
                .layout
                .buildDirectory
                .file(JibDockerBuildConvention.imageFingerprintFileName)
                .get()
                .asFile
        }
        tasks.register<Test>("containerBasedServiceTest") {
            description = "Runs container-based service tests."
            group = "verification"
            useJUnitPlatform()

            testClassesDirs = sourceSet.output.classesDirs
            classpath = configurations[sourceSet.runtimeClasspathConfigurationName] + sourceSet.output
            dependsOn(extension.starterModuleName.map { ":$it:jibDockerBuild" })
            dependsOn(extension.starterModuleName.map { ":$it:${JibDockerBuildConvention.imageFingerprintTaskName}" })
            inputs.file(imageFingerprint)
                .withPropertyName("serviceImageFingerprint")
                .withPathSensitivity(PathSensitivity.NONE)
        }
        Unit
    }

    /** Extension for configuring container-based service tests. */
    abstract class Extension {

        /** The Gradle module name containing the Jib-built Docker image that the tests run against. */
        abstract val starterModuleName: Property<String>
    }
}

/** Shorthand for adding a dependency to the `containerBasedServiceTestImplementation` configuration. */
fun DependencyHandlerScope.containerBasedServiceTestImplementation(dependency: Any) = "containerBasedServiceTestImplementation"(dependency)
