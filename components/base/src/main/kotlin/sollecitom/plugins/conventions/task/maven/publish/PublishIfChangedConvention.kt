package sollecitom.plugins.conventions.task.maven.publish

import org.gradle.api.Plugin
import org.gradle.api.Project

abstract class PublishIfChangedConvention : Plugin<Project> {

    override fun apply(project: Project) = with(project) {
        check(project == rootProject) { "sollecitom.publish-if-changed-conventions must be applied to the root project." }

        registerWritePublicationState { candidate -> "${candidate.name}-maven" }
    }
}
