package sollecitom.plugins

import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import java.net.URI

/** Configures Maven repositories for different contexts (build scripts, module dependencies, publications). Internal packages are resolved from Maven Local and GitHub Packages; external packages from Maven Central. */
object RepositoryConfiguration {

    private const val internalGroup = "${ProjectSettings.rootGroupId}.*"

    private data class GithubCredentials(
        val username: String,
        val token: String,
    )

    /** Repositories for resolving buildscript dependencies (plugins, classpath). */
    object BuildScript {

        fun apply(config: RepositoryHandler) {
            config.mavenLocal()
            config.mavenCentral()
        }
    }

    /** Adds a GitHub Packages Maven repository scoped to internal group packages. Credentials are read from project properties or environment variables. */
    object GithubPackages {

        fun apply(config: RepositoryHandler, project: Project) {
            val credentials = resolveCredentials(project) ?: run {
                project.logger.info("Skipping GitHub Packages repository because no credentials are configured.")
                return
            }

            config.maven {
                url = URI.create("https://maven.pkg.github.com/acme/*") // TODO fix to be GitLab, etc.
                credentials {
                    username = credentials.username
                    password = credentials.token
                }
                content {
                    includeGroupByRegex(internalGroup)
                }
            }
        }

        private fun resolveCredentials(project: Project): GithubCredentials? {
            val username = (project.findProperty("acme.github.user") as String? ?: System.getenv("GITHUB_USERNAME"))
                ?.takeIf { it.isNotBlank() }
            val token = (project.findProperty("acme.github.token") as String? ?: System.getenv("GITHUB_TOKEN"))
                ?.takeIf { it.isNotBlank() }

            return if (username != null && token != null) {
                GithubCredentials(username = username, token = token)
            } else {
                null
            }
        }
    }

    /** Repositories for publishing artifacts (Maven Local + GitHub Packages). */
    object Publications {

        fun apply(config: RepositoryHandler, project: Project) {

            config.mavenLocal()
            GithubPackages.apply(config, project)
        }
    }

    /** Repositories for resolving module (project) dependencies. Routes internal packages to Maven Local/GitHub Packages and external packages to Maven Central and Confluent. */
    object Modules {

        fun apply(config: RepositoryHandler, project: Project) {

            config.mavenCentral {
                content {
                    excludeGroupByRegex(internalGroup)
                }
            }

            config.mavenLocal {
                content {
                    includeGroupByRegex(internalGroup)
                }
            }

            config.maven {
                url = URI.create("https://packages.confluent.io/maven")
                content {
                    excludeGroupByRegex(internalGroup)
                }
            }

            GithubPackages.apply(config, project)
        }
    }
}
