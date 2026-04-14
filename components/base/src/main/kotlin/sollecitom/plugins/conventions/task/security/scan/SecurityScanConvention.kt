package sollecitom.plugins.conventions.task.security.scan

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.DependencyHandlerScope
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.register
import org.gradle.process.CommandLineArgumentProvider
import sollecitom.plugins.conventions.task.jib.JibDockerBuildConvention
import javax.inject.Inject

/**
 * Convention plugin that creates a `securityScan` source set and task for scanning Docker images
 * for vulnerabilities using Trivy (via Testcontainers). Depends on a Jib-built Docker image.
 */
abstract class SecurityScanConvention : Plugin<Project> {

    override fun apply(project: Project) = with(project) {

        val sourceSet = extensions.getByType<JavaPluginExtension>().sourceSets.create("securityScan")
        val extension = project.extensions.create<Extension>("securityScan")
        val imageFingerprint = providers.provider {
            project.project(":${extension.starterModuleName.get()}")
                .layout
                .buildDirectory
                .file(JibDockerBuildConvention.imageFingerprintFileName)
                .get()
                .asFile
        }
        tasks.register<Test>("securityScan") {
            description = "Scans Docker images for security vulnerabilities using Trivy."
            group = "verification"
            useJUnitPlatform()

            testClassesDirs = sourceSet.output.classesDirs
            classpath = configurations[sourceSet.runtimeClasspathConfigurationName] + sourceSet.output
            dependsOn(extension.starterModuleName.map { ":$it:jibDockerBuild" })
            dependsOn(extension.starterModuleName.map { ":$it:${JibDockerBuildConvention.imageFingerprintTaskName}" })
            inputs.file(imageFingerprint)
                .withPropertyName("serviceImageFingerprint")
                .withPathSensitivity(PathSensitivity.NONE)

            jvmArgumentProviders += objects.newInstance<SecurityScanArgumentProvider>().apply {
                imageName.set(extension.imageName)
                severities.set(extension.severities)
                trivyVersion.set(extension.trivyVersion)
                trivyIgnoreFile.set(project.providers.provider {
                    extension.trivyIgnoreFile.orNull?.let(project.layout.projectDirectory::file)
                })
            }
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

        /** Trivy Docker image version. Defaults to the version bundled in the security-scan library. */
        @get:Optional
        abstract val trivyVersion: Property<String>
    }

    abstract class SecurityScanArgumentProvider @Inject constructor(
        objects: ObjectFactory
    ) : CommandLineArgumentProvider {

        @get:Input
        abstract val imageName: Property<String>

        @get:Input
        @get:Optional
        abstract val severities: ListProperty<String>

        @get:InputFile
        @get:Optional
        @get:PathSensitive(PathSensitivity.RELATIVE)
        val trivyIgnoreFile: RegularFileProperty = objects.fileProperty()

        @get:Input
        @get:Optional
        abstract val trivyVersion: Property<String>

        override fun asArguments(): Iterable<String> = buildList {
            add("-DsecurityScan.imageName=${imageName.get()}")
            severities.orNull?.takeIf { it.isNotEmpty() }?.let {
                add("-DsecurityScan.severities=${it.joinToString(",")}")
            }
            trivyIgnoreFile.orNull?.asFile?.absolutePath?.let {
                add("-DsecurityScan.trivyIgnoreFile=$it")
            }
            trivyVersion.orNull?.let {
                add("-DsecurityScan.trivyVersion=$it")
            }
        }
    }
}

/** Shorthand for adding a dependency to the `securityScanImplementation` configuration. */
fun DependencyHandlerScope.securityScanImplementation(dependency: Any) = "securityScanImplementation"(dependency)
