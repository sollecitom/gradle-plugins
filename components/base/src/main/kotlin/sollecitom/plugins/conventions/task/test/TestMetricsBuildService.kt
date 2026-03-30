package sollecitom.plugins.conventions.task.test

import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.util.concurrent.atomic.AtomicLong

/** A Gradle shared build service that accumulates test counts across all projects and prints an aggregate summary on close. */
abstract class TestMetricsBuildService : BuildService<TestMetricsBuildService.Params>, AutoCloseable {

    interface Params : BuildServiceParameters {
        val projectName: Property<String>
    }

    private val testCount = AtomicLong(0L)
    private val successfulTestCount = AtomicLong(0L)
    private val failedTestCount = AtomicLong(0L)
    private val skippedTestCount = AtomicLong(0L)

    /** Atomically records test results from a single project's test suite. Thread-safe for concurrent test task execution. */
    fun recordResults(total: Long, successful: Long, failed: Long, skipped: Long) {
        testCount.addAndGet(total)
        successfulTestCount.addAndGet(successful)
        failedTestCount.addAndGet(failed)
        skippedTestCount.addAndGet(skipped)
    }

    override fun close() {
        println()
        println("> ${parameters.projectName.get()}: Aggregated Test Metrics:")
        println("\t>   Total Tests Run:     ${testCount.get()}")
        println("\t>   Total Tests Passed:  ${successfulTestCount.get()}")
        println("\t>   Total Tests Failed:  ${failedTestCount.get()}")
        println("\t>   Total Tests Skipped: ${skippedTestCount.get()}")
    }

    companion object {
        const val SERVICE_NAME = "testMetricsBuildService"
    }
}
