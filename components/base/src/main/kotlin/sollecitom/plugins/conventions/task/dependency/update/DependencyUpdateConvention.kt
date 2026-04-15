package sollecitom.plugins.conventions.task.dependency.update

import nl.littlerobots.vcu.plugin.VersionCatalogUpdateExtension
import nl.littlerobots.vcu.plugin.VersionCatalogUpdatePlugin
import nl.littlerobots.vcu.plugin.resolver.ModuleVersionSelector
import nl.littlerobots.vcu.plugin.resolver.VersionSelectors
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Optional
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register
import sollecitom.plugins.RepositoryConfiguration

/** Convention plugin that configures the version-catalog-update plugin for automated dependency updates. */
abstract class DependencyUpdateConvention : Plugin<Project> {

    override fun apply(project: Project) = with(project) {

        pluginManager.apply(VersionCatalogUpdatePlugin::class)
        val extension = project.extensions.create<Extension>("versionCatalog")
        extension.internalGroups.convention(listOf("sollecitom"))
        RepositoryConfiguration.Modules.apply(repositories, project)

        extensions.configure<VersionCatalogUpdateExtension> {
            sortByKey.set(false)
            keep {
                keepUnusedVersions.set(true)
            }
            versionSelector(extension.versionSelector.getOrElse(VersionSelectors.PREFER_STABLE))
        }

        tasks.register<UpdateInternalCatalogVersionsTask>("updateInternalCatalogVersions") {
            catalogFile.set(layout.projectDirectory.file("gradle/libs.versions.toml"))
            internalGroups.set(extension.internalGroups)
        }
        tasks.register<UpdateSummaryTask>("updateSummary")
        Unit
    }

    /** Extension for customizing dependency update behavior. */
    abstract class Extension {

        /** Strategy for selecting dependency versions. Defaults to [VersionSelectors.PREFER_STABLE]. */
        @get:Optional
        abstract val versionSelector: Property<ModuleVersionSelector>

        /** Group or plugin-id prefixes treated as internal and resolved from the local Maven repository. */
        abstract val internalGroups: ListProperty<String>
    }
}
