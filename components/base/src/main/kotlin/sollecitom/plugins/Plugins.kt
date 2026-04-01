package sollecitom.plugins

import org.gradle.api.JavaVersion
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec

/** Centralised configuration for Gradle plugins applied by convention plugins. */
object Plugins {

    /** Configures the Java plugin with the project's target JVM toolchain, sources JAR, and javadoc JAR. */
    object JavaPlugin {

        /** Applies the standard Java plugin configuration to the given [plugin] extension. */
        fun configure(plugin: JavaPluginExtension) {
            with(plugin) {
                toolchain {
                    languageVersion.set(JavaLanguageVersion.of(JavaVersion.VERSION_25.majorVersion))
                    vendor.set(JvmVendorSpec.ADOPTIUM)
                }
                withJavadocJar()
                withSourcesJar()
            }
        }
    }
}