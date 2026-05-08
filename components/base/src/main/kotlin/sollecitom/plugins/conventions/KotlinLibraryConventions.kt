package sollecitom.plugins.conventions

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.ide.idea.model.IdeaModel
import sollecitom.plugins.Plugins
import sollecitom.plugins.RepositoryConfiguration
import sollecitom.plugins.conventions.task.dependency.version.MinimumDependencyVersion
import sollecitom.plugins.conventions.task.dependency.version.MinimumDependencyVersionConventions
import sollecitom.plugins.conventions.task.kotlin.KotlinTaskConventions
import sollecitom.plugins.conventions.task.test.TestTaskConventions

/** Convention plugin for Kotlin JVM libraries. Applies Kotlin JVM, java-library, IDEA, test, and dependency version conventions, and configures reproducible archives. */
abstract class KotlinLibraryConventions : Plugin<Project> {

    override fun apply(project: Project) = with(project) {

        pluginManager.apply("org.jetbrains.kotlin.jvm")
        pluginManager.apply("java-library")
        pluginManager.apply("idea")
        pluginManager.apply(KotlinTaskConventions::class)
        pluginManager.apply(TestTaskConventions::class)
        pluginManager.apply(MinimumDependencyVersionConventions::class)

        val projectGroup = findProperty("projectGroup")?.toString()
        val currentVersion = findProperty("currentVersion")?.toString()
        if (projectGroup != null) group = projectGroup
        if (currentVersion != null) version = currentVersion

        RepositoryConfiguration.Modules.apply(repositories, project)

        extensions.getByType<IdeaModel>().apply {
            module { inheritOutputDirs = true }
        }

        extensions.configure<JavaPluginExtension> {
            Plugins.JavaPlugin.configure(this)
        }

        tasks.withType<AbstractArchiveTask>().configureEach {
            isPreserveFileTimestamps = false
            isReproducibleFileOrder = true
        }

        tasks.withType<Javadoc>().configureEach {
            (options as? StandardJavadocDocletOptions)?.addBooleanOption("notimestamp", true)
        }

        extensions.configure<MinimumDependencyVersionConventions.Extension> {
            knownVulnerableDependencies.set(defaultVulnerableDependencies)
        }
        Unit
    }

    companion object {
        // Netty 4.1.133.Final fixes CVE-2026-42577/42578/42579/42580/42581/42582/42583/42584/42585/42586/42587/41417/44248
        // (epoll DoS via RST, handler-proxy auth, codec-dns input validation, codec-http desync/decompression bombs,
        //  codec-redis, codec-mqtt, codec-http3, codec compression resource exhaustion). Pin all impacted modules
        //  uniformly so transitive callers (Pulsar, gRPC, http4k, Jetty) cannot drag in older vulnerable versions.
        private const val NETTY_MIN_VERSION = "4.1.133.Final"
        private val nettyModules = listOf(
            "netty-handler",
            "netty-handler-proxy",
            "netty-codec",
            "netty-codec-http",
            "netty-codec-http2",
            "netty-codec-http3",
            "netty-codec-dns",
            "netty-codec-mqtt",
            "netty-codec-redis",
            "netty-transport-native-epoll",
        )
        private val defaultVulnerableDependencies: List<MinimumDependencyVersion> = listOf(
            // CVE fix: versions before 1.26.0 have known vulnerabilities
            MinimumDependencyVersion(group = "org.apache.commons", name = "commons-compress", minimumVersion = "1.26.0"),
        ) + nettyModules.map { module -> MinimumDependencyVersion(group = "io.netty", name = module, minimumVersion = NETTY_MIN_VERSION) }
    }
}
