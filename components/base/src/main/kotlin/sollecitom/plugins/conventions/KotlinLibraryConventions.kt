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
        // Netty 4.2.18.Final is the current CVE-clean 4.2.x release. Superseded pins: 4.2.13.Final carried the
        // netty-handler/resolver-dns family (CVE-2026-44249/45416/50010/45674/47691); 4.2.15.Final then exposed
        // netty-codec-http CVE-2026-55831/55833/56745 and netty-codec-compression CVE-2026-59901 (Bzip2Decoder
        // RLE infinite loop → event-loop hang), all fixed in 4.2.16.Final; 4.2.16.Final in turn carried
        // netty-handler CVE-2026-75595, fixed in 4.2.17.Final. Pinning the whole `io.netty:*` group prevents
        // 4.1↔4.2 module mixing (where, e.g., a 4.1.x resolver-dns would call into a 4.2.x codec-dns and break
        // at runtime).
        private val defaultVulnerableDependencies: List<MinimumDependencyVersion> = listOf(
            // CVE fix: versions before 1.26.0 have known vulnerabilities
            MinimumDependencyVersion(group = "org.apache.commons", name = "commons-compress", minimumVersion = "1.26.0"),
            MinimumDependencyVersion(group = "io.netty", name = "*", minimumVersion = "4.2.18.Final"),
            // CVE-2026-53712: scram 3.2 silently downgrades SCRAM channel-binding auth on unsupported certificate
            // algorithms; fixed in 3.3. Pulled in transitively via the PostgreSQL driver. Pinning the whole
            // `com.ongres.scram:*` group keeps scram-client/scram-common in lockstep at a fixed version.
            MinimumDependencyVersion(group = "com.ongres.scram", name = "*", minimumVersion = "3.3"),
            // GHSA-r7wm-3cxj-wff9: jackson-core < 2.22.1 lets the async parser bypass maxNumberLength via chunked
            // digit accumulation (incomplete fix for GHSA-72hv-8253-57qq); fixed in 2.22.1. Pin jackson-core BY NAME,
            // not the `com.fasterxml.jackson.core:*` group — jackson-annotations shares that group but has no 2.22.1
            // release, so a group-wide pin would force a non-existent version and break resolution.
            MinimumDependencyVersion(group = "com.fasterxml.jackson.core", name = "jackson-core", minimumVersion = "2.22.1"),
            // CVE-2026-8763 (Name Constraints bypass via a trailing dot in rfc822Name/URI) and CVE-2026-13506
            // (DoS via lazy ASN.1 sequence processing) affect bcprov < 1.85; 1.86 is the current clean release.
            // Pulled in transitively via the Pulsar client. Pin the three `-jdk18on` artifacts BY NAME, not the
            // `org.bouncycastle:*` group — the NATS client brings in bcprov-lts8on, which is on an unrelated 2.x
            // line, so a group-wide pin would force a non-existent version and break resolution.
            MinimumDependencyVersion(group = "org.bouncycastle", name = "bcprov-jdk18on", minimumVersion = "1.86"),
            MinimumDependencyVersion(group = "org.bouncycastle", name = "bcpkix-jdk18on", minimumVersion = "1.86"),
            MinimumDependencyVersion(group = "org.bouncycastle", name = "bcutil-jdk18on", minimumVersion = "1.86"),
        )
    }
}
