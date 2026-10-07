package sollecitom.buildsrc.publish

import org.gradle.api.Project
import sollecitom.plugins.conventions.task.maven.publish.registerWritePublicationState

fun Project.registerPluginPublicationState() = registerWritePublicationState { "pluginMaven" }
