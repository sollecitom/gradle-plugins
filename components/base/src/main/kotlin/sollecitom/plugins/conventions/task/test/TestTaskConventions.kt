package sollecitom.plugins.conventions.task.test

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestResult
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.kotlin.dsl.KotlinClosure2
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.withType
import sollecitom.plugins.JvmConfiguration

abstract class TestTaskConventions : Plugin<Project> {

    override fun apply(project: Project) {

        @Suppress("UNCHECKED_CAST")
        val serviceProvider = project.rootProject.extra[TestMetricsBuildService.SERVICE_NAME] as Provider<TestMetricsBuildService>

        project.tasks.withType<Test>().configureEach {
            usesService(serviceProvider)
            useJUnitPlatform()
            if (isRunningOnRemoteBuildEnvironment()) {
                maxParallelForks = 1
                maxHeapSize = "1g"
            } else {
                maxParallelForks = Runtime.getRuntime().availableProcessors() * 2
            }
            testLogging {
                showStandardStreams = false
                exceptionFormat = TestExceptionFormat.FULL
            }
            jvmArgs = JvmConfiguration.testArgs

            reports {
                junitXml.outputLocation.set(project.file("${project.rootProject.layout.buildDirectory.get()}/test-results/test/${project.name}"))
                html.outputLocation.set(project.file("${project.rootProject.layout.buildDirectory.get()}/test-results/reports/test/${project.name}"))
            }
            afterSuite(
                KotlinClosure2({ descriptor: TestDescriptor, result: TestResult ->
                    // Only execute on the outermost suite
                    if (descriptor.parent == null) {
                        println("\t>   Result:  ${result.resultType}")
                        println("\t>   Tests:   ${result.testCount}")
                        println("\t>   Passed:  ${result.successfulTestCount}")
                        println("\t>   Failed:  ${result.failedTestCount}")
                        println("\t>   Skipped: ${result.skippedTestCount}")
                        serviceProvider.get().recordResults(
                            result.testCount, result.successfulTestCount,
                            result.failedTestCount, result.skippedTestCount
                        )
                    }
                })
            )
        }
    }

    private fun isRunningOnRemoteBuildEnvironment() = System.getenv("CI") != null
}
