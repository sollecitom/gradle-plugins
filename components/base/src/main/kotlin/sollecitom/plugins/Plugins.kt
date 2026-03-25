package sollecitom.plugins

import org.gradle.api.JavaVersion
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion

object Plugins {

    object JavaPlugin {

        fun configure(plugin: JavaPluginExtension) {
            with(plugin) {
                toolchain {
                    languageVersion.set(JavaLanguageVersion.of(JavaVersion.VERSION_25.majorVersion))
                }
                withJavadocJar()
                withSourcesJar()
            }
        }
    }
}