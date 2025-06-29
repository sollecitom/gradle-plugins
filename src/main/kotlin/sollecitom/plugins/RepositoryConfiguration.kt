package sollecitom.plugins

import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import java.net.URI

object RepositoryConfiguration {

    private const val internalGroup = "${ProjectSettings.rootGroupId}.*"

    object BuildScript {

        fun apply(config: RepositoryHandler) {
            config.mavenLocal()
            config.mavenCentral()
        }
    }

    object GithubPackages {

        fun apply(config: RepositoryHandler, project: Project) {

            config.maven {
                url = URI.create("https://maven.pkg.github.com/acme/*") // TODO fix to be GitLab, etc.
                credentials {
                    username = project.findProperty("acme.github.user") as String? ?: System.getenv("GITHUB_USERNAME")
                    password = project.findProperty("acme.github.token") as String? ?: System.getenv("GITHUB_TOKEN")
                }
                content {
                    includeGroupByRegex(internalGroup)
                }
            }
        }
    }

    object Publications {

        fun apply(config: RepositoryHandler, project: Project) {

            config.mavenLocal()
            GithubPackages.apply(config, project)
        }
    }

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
