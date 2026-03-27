package sollecitom.plugins.conventions.task.dependency.update

import nl.littlerobots.vcu.plugin.VersionCatalogUpdateExtension
import nl.littlerobots.vcu.plugin.VersionCatalogUpdatePlugin
import nl.littlerobots.vcu.plugin.resolver.ModuleVersionSelector
import nl.littlerobots.vcu.plugin.resolver.VersionSelectors
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Optional
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create

abstract class DependencyUpdateConvention : Plugin<Project> {

    override fun apply(project: Project) = with(project) {

        pluginManager.apply(VersionCatalogUpdatePlugin::class)
        val extension = project.extensions.create<Extension>("versionCatalog")

        afterEvaluate {
            extensions.configure<VersionCatalogUpdateExtension> {
                sortByKey.set(false)
                keep {
                    keepUnusedVersions.set(true)
                }
                versionSelector(extension.versionSelector.getOrElse(VersionSelectors.PREFER_STABLE))
            }
        }
    }

    abstract class Extension {

        @get:Optional
        abstract val versionSelector: Property<ModuleVersionSelector>
    }
}