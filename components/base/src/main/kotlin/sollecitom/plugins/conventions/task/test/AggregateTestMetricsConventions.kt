package sollecitom.plugins.conventions.task.test

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.extra

abstract class AggregateTestMetricsConventions : Plugin<Project> {

    override fun apply(project: Project) {

        val serviceProvider = project.gradle.sharedServices.registerIfAbsent(
            TestMetricsBuildService.SERVICE_NAME, TestMetricsBuildService::class.java
        ) {
            parameters.projectName.set(project.name)
        }
        project.extra[TestMetricsBuildService.SERVICE_NAME] = serviceProvider
    }
}
