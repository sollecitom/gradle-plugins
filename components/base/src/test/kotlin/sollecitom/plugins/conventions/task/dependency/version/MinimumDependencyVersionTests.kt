package sollecitom.plugins.conventions.task.dependency.version

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ModuleVersionSelector
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS

@TestInstance(PER_CLASS)
class MinimumDependencyVersionTests {

    private val minimum = MinimumDependencyVersion(group = "com.fasterxml.jackson.core", name = "jackson-databind", minimumVersion = "2.22.3")

    @Test
    fun `a requested version below the minimum violates it`() {

        assertThat(minimum.isViolatedBy(requested("com.fasterxml.jackson.core", "jackson-databind", "2.9.10"))).isTrue()
    }

    @Test
    fun `a requested version equal to the minimum does not violate it`() {

        assertThat(minimum.isViolatedBy(requested("com.fasterxml.jackson.core", "jackson-databind", "2.22.3"))).isFalse()
    }

    @Test
    fun `a requested version above the minimum does not violate it`() {

        assertThat(minimum.isViolatedBy(requested("com.fasterxml.jackson.core", "jackson-databind", "2.23.0"))).isFalse()
    }

    @Test
    fun `a requested version that cannot be compared to the minimum violates it`() {

        assertThat(minimum.isViolatedBy(requested("com.fasterxml.jackson.core", "jackson-databind", "[2.0,3.0)"))).isTrue()
        assertThat(minimum.isViolatedBy(requested("com.fasterxml.jackson.core", "jackson-databind", null))).isTrue()
    }

    @Test
    fun `a dependency with other coordinates never violates it`() {

        assertThat(minimum.isViolatedBy(requested("com.fasterxml.jackson.core", "jackson-core", "2.0.0"))).isFalse()
    }

    @Test
    fun `a wildcard name applies to every artifact in the group`() {

        val groupMinimum = MinimumDependencyVersion(group = "io.netty", name = "*", minimumVersion = "4.2.18.Final")

        assertThat(groupMinimum.isViolatedBy(requested("io.netty", "netty-codec-http", "4.2.9.Final"))).isTrue()
        assertThat(groupMinimum.isViolatedBy(requested("io.netty", "netty-handler", "4.2.19.Final"))).isFalse()
    }

    private fun requested(group: String, name: String, version: String?): ModuleVersionSelector = object : ModuleVersionSelector {
        override fun getGroup() = group
        override fun getName() = name
        override fun getVersion() = version
        override fun matchesStrictly(identifier: ModuleVersionIdentifier) = identifier.group == group && identifier.name == name && identifier.version == version
        override fun getModule(): ModuleIdentifier = object : ModuleIdentifier {
            override fun getGroup() = group
            override fun getName() = name
        }
    }
}
