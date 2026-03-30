package sollecitom.plugins.conventions.task.security.scan

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.DependencyHandlerScope
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register

/**
 * Convention plugin that creates a `securityScan` source set and task for scanning Docker images
 * for vulnerabilities using Trivy (via Testcontainers). Depends on a Jib-built Docker image.
 */
abstract class SecurityScanConvention : Plugin<Project> {

    override fun apply(project: Project) = with(project) {

        val sourceSet = extensions.getByType<JavaPluginExtension>().sourceSets.create("securityScan")
        val extension = project.extensions.create<Extension>("securityScan")
        tasks.register<Test>("securityScan") {
            description = "Scans Docker images for security vulnerabilities using Trivy."
            group = "verification"
            useJUnitPlatform()

            testClassesDirs = sourceSet.output.classesDirs
            classpath = configurations[sourceSet.runtimeClasspathConfigurationName] + sourceSet.output
            dependsOn(extension.starterModuleName.map { ":$it:jibDockerBuild" })

            // Pass configuration as system properties to the test
            systemProperty("securityScan.imageName", extension.imageName.map { it }.getOrElse(""))
            extension.severities.orNull?.let { systemProperty("securityScan.severities", it.joinToString(",")) }
            extension.trivyIgnoreFile.orNull?.let { systemProperty("securityScan.trivyIgnoreFile", it) }
        }
        Unit
    }

    /** Extension for configuring Docker image security scans. */
    abstract class Extension {

        /** The Gradle module name containing the Jib-built Docker image (required). */
        abstract val starterModuleName: Property<String>

        /** The full Docker image name to scan (required). */
        abstract val imageName: Property<String>

        /** Vulnerability severities that cause the scan to fail. Defaults to CRITICAL, HIGH. */
        @get:Optional
        abstract val severities: ListProperty<String>

        /** Path to a .trivyignore file for suppressing specific CVEs. Defaults to .trivyignore in project root. */
        @get:Optional
        abstract val trivyIgnoreFile: Property<String>
    }
}

/** Shorthand for adding a dependency to the `securityScanImplementation` configuration. */
fun DependencyHandlerScope.securityScanImplementation(dependency: Any) = "securityScanImplementation"(dependency)
