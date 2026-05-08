package sollecitom.plugins.conventions.task.dependency.version

import sollecitom.plugins.conventions.task.dependency.update.DependencyVersion
import sollecitom.plugins.conventions.task.dependency.update.invoke
import org.gradle.api.artifacts.ModuleVersionSelector

/** A dependency constraint that enforces a [minimumVersion] for a given [group]:[name] coordinate. Pass `name = "*"` to apply to every artifact in [group]. */
data class MinimumDependencyVersion(val group: String, val name: String, val minimumVersion: DependencyVersion) {

    constructor(group: String, name: String, minimumVersion: String) : this(group = group, name = name, minimumVersion = DependencyVersion.Companion(minimumVersion))

    /** Returns true if the [requested] dependency matches this constraint's group and (name or wildcard). */
    fun matches(requested: ModuleVersionSelector): Boolean = requested.group == group && (name == "*" || requested.name == name)
}