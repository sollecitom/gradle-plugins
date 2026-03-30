package sollecitom.plugins.conventions.task.kotlin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/** Convention plugin that configures all KotlinCompile tasks with the target JVM version, progressive mode, context parameters, and strict JSR-305 nullability. */
abstract class KotlinTaskConventions : Plugin<Project> {

    override fun apply(project: Project) = with(project) {

        tasks.withType<KotlinCompile>().configureEach {

            compilerOptions {
                jvmTarget.set(targetJvmVersion)
                javaParameters.set(true)
                freeCompilerArgs.set(compilerArgs)
                progressiveMode.set(true)
            }
        }
    }

    companion object {
        private val compilerArgs = listOf("-Xcontext-parameters", "-Xjsr305=strict")
        private val targetJvmVersion = JvmTarget.JVM_25
    }
}