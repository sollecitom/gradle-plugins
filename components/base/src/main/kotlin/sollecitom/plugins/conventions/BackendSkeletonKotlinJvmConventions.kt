package sollecitom.plugins.conventions

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import sollecitom.plugins.RepositoryConfiguration

abstract class BackendSkeletonKotlinJvmConventions : Plugin<Project> {

    override fun apply(project: Project) = with(project) {
        pluginManager.apply("java-library")
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        pluginManager.apply("idea")

        val projectGroup = findProperty("projectGroup")?.toString()
        val currentVersion = findProperty("currentVersion")?.toString()
        if (projectGroup != null) group = projectGroup
        if (currentVersion != null) version = currentVersion

        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(25))
                vendor.set(JvmVendorSpec.ADOPTIUM)
            }
            withJavadocJar()
            withSourcesJar()
        }

        tasks.withType<KotlinCompile>().configureEach {
            compilerOptions {
                javaParameters.set(true)
                progressiveMode.set(true)
                freeCompilerArgs.addAll(
                    "-Xjsr305=strict",
                    "-Xcontext-parameters",
                )
            }
        }

        extensions.getByType<IdeaModel>().module { inheritOutputDirs = true }

        RepositoryConfiguration.Modules.apply(repositories, this)

        configurations.all {
            resolutionStrategy.eachDependency {
                if (requested.group == "org.apache.commons" && requested.name == "commons-compress") {
                    useVersion("1.26.0")
                    because("CVE fix: versions before 1.26.0 have known vulnerabilities")
                }
            }
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            maxParallelForks = if (System.getenv("CI") != null) 1 else (Runtime.getRuntime().availableProcessors() * 2)
            if (System.getenv("CI") != null) maxHeapSize = "1g"
            testLogging {
                showStandardStreams = false
                exceptionFormat = TestExceptionFormat.FULL
                events("passed", "skipped", "failed")
            }
            reports {
                junitXml.outputLocation.set(project.file("${project.rootProject.layout.buildDirectory.get()}/test-results/test/${project.name}"))
                html.outputLocation.set(project.file("${project.rootProject.layout.buildDirectory.get()}/test-results/reports/test/${project.name}"))
            }
        }

        tasks.withType<AbstractArchiveTask>().configureEach {
            isPreserveFileTimestamps = false
            isReproducibleFileOrder = true
        }
    }
}
