package sollecitom.plugins.conventions

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import sollecitom.plugins.RepositoryConfiguration
import sollecitom.plugins.conventions.task.dependency.version.MinimumDependencyVersion
import sollecitom.plugins.conventions.task.test.TestTaskConventions

abstract class BackendSkeletonKotlinJvmConventions : Plugin<Project> {

    override fun apply(project: Project) = with(project) {
        pluginManager.apply("java-library")
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        pluginManager.apply("idea")
        pluginManager.apply(TestTaskConventions::class)

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
                freeCompilerArgs.add("-Xjsr305=strict")
                optIn.add("kotlin.uuid.ExperimentalUuidApi")
            }
        }

        extensions.getByType<IdeaModel>().module { inheritOutputDirs = true }

        RepositoryConfiguration.Modules.apply(repositories, this)

        val commonsCompress = MinimumDependencyVersion(group = "org.apache.commons", name = "commons-compress", minimumVersion = "1.26.0")
        configurations.all {
            resolutionStrategy.eachDependency {
                if (commonsCompress.isViolatedBy(requested)) {
                    useVersion(commonsCompress.minimumVersion.stringValue)
                    because("CVE fix: versions before 1.26.0 have known vulnerabilities")
                }
            }
        }

        tasks.withType<AbstractArchiveTask>().configureEach {
            isPreserveFileTimestamps = false
            isReproducibleFileOrder = true
        }

        tasks.withType<Javadoc>().configureEach {
            (options as? StandardJavadocDocletOptions)?.addBooleanOption("notimestamp", true)
        }
    }
}
