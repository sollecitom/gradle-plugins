package sollecitom.plugins.conventions.task.test

import org.gradle.api.Plugin
import org.gradle.api.Project

/** Root-project convention plugin that registers the shared [TestMetricsBuildService] for aggregating test metrics across all subprojects. */
abstract class AggregateTestMetricsConventions : Plugin<Project> {

    override fun apply(project: Project) {

        project.gradle.sharedServices.registerIfAbsent(TestMetricsBuildService.SERVICE_NAME, TestMetricsBuildService::class.java) {
            parameters.projectName.set(project.name)
        }
    }
}
