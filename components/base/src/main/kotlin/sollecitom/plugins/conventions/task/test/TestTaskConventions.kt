package sollecitom.plugins.conventions.task.test

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.withType
import sollecitom.plugins.JvmConfiguration

/** Convention plugin that configures all Test tasks with JUnit Platform, parallel execution, logging, and aggregated metrics reporting. Reduces parallelism on CI environments. */
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
            addTestListener(object : TestListener {
                override fun beforeSuite(suite: TestDescriptor) {}
                override fun beforeTest(testDescriptor: TestDescriptor) {}
                override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {}
                override fun afterSuite(descriptor: TestDescriptor, result: TestResult) {
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
                }
            })
        }
    }

    private fun isRunningOnRemoteBuildEnvironment() = System.getenv("CI") != null
}
