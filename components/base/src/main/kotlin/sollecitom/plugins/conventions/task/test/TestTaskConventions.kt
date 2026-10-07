package sollecitom.plugins.conventions.task.test

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.kotlin.dsl.withType
import sollecitom.plugins.JvmConfiguration

/** Convention plugin that configures all Test tasks with JUnit Platform, logging, and aggregated metrics reporting. */
abstract class TestTaskConventions : Plugin<Project> {

    override fun apply(project: Project) {

        val serviceProvider = project.gradle.sharedServices.registerIfAbsent(TestMetricsBuildService.SERVICE_NAME, TestMetricsBuildService::class.java) {
            parameters.projectName.set(project.rootProject.name)
        }

        project.tasks.withType<Test>().configureEach {
            usesService(serviceProvider)
            useJUnitPlatform()
            systemProperty("junit.jupiter.execution.timeout.testable.method.default", DEFAULT_TEST_TIMEOUT)
            if (isRunningOnRemoteBuildEnvironment()) {
                maxHeapSize = "1g"
            }
            testLogging {
                showStandardStreams = false
                exceptionFormat = TestExceptionFormat.FULL
            }
            jvmArgs = JvmConfiguration.testArgs

            reports {
                junitXml.outputLocation.set(project.file("${project.rootProject.layout.buildDirectory.get()}/test-results/${this@configureEach.name}/${project.name}"))
                html.outputLocation.set(project.file("${project.rootProject.layout.buildDirectory.get()}/test-results/reports/${this@configureEach.name}/${project.name}"))
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

    private companion object {
        const val DEFAULT_TEST_TIMEOUT = "2m"
    }
}
