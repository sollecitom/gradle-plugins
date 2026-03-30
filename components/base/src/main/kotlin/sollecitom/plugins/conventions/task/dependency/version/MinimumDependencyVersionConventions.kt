package sollecitom.plugins.conventions.task.dependency.version

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Optional
import org.gradle.kotlin.dsl.create

/** Convention plugin that enforces minimum dependency versions to mitigate known vulnerabilities via Gradle resolution rules. */
abstract class MinimumDependencyVersionConventions : Plugin<Project> {

    override fun apply(project: Project) = with(project) {

        val settings = project.extensions.create<Extension>("minimumDependencyVersionConventions")
        project.configurations.configureEach {
            resolutionStrategy.eachDependency {
                // https://docs.gradle.org/current/userguide/resolution_rules.html
                settings.vulnerableDependencies.forEach { vulnerableDependency ->
                    if (vulnerableDependency.matches(requested)) {
                        useVersion(vulnerableDependency.minimumVersion.stringValue)
                    }
                }
            }
        }
    }

    private val Extension.vulnerableDependencies: List<MinimumDependencyVersion> get() = knownVulnerableDependencies.getOrElse(Extension.Companion.defaultVulnerableDependencies)

    /** Extension for specifying dependencies with known vulnerabilities that require minimum version enforcement. */
    abstract class Extension {

        /** List of dependencies with known vulnerabilities. Defaults to an empty list. */
        @get:Optional
        abstract val knownVulnerableDependencies: ListProperty<MinimumDependencyVersion>

        internal companion object {
            val defaultVulnerableDependencies: List<MinimumDependencyVersion> = emptyList()
        }
    }
}