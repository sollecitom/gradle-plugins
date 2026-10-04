package sollecitom.plugins.conventions.task.maven.publish

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS

@TestInstance(PER_CLASS)
class PublicationHashGateTests {

    @Test
    fun `a routine change publishes the next patch`() {

        assertThat(PublicationHashGate.nextVersion(latestPublishedVersion = "1.0.35", currentVersion = "1.0.35")).isEqualTo("1.0.36")
    }

    @Test
    fun `a deliberate bump above the published version is published exactly`() {

        assertThat(PublicationHashGate.nextVersion(latestPublishedVersion = "1.0.35", currentVersion = "2.0.0")).isEqualTo("2.0.0")
        assertThat(PublicationHashGate.nextVersion(latestPublishedVersion = "1.0.35", currentVersion = "1.1.0")).isEqualTo("1.1.0")
    }

    @Test
    fun `a current version behind the published one publishes the next patch after the published one`() {

        assertThat(PublicationHashGate.nextVersion(latestPublishedVersion = "1.0.35", currentVersion = "1.0.0")).isEqualTo("1.0.36")
    }

    @Test
    fun `a non-release current version publishes the next patch after the published one`() {

        assertThat(PublicationHashGate.nextVersion(latestPublishedVersion = "1.0.35", currentVersion = "1.0.0-SNAPSHOT")).isEqualTo("1.0.36")
    }
}
