plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.semver4j)
}

sourceSets.main {
    kotlin {
        srcDir("../components/base/src/main/kotlin")
        include(
            "sollecitom/buildsrc/**",
            "sollecitom/plugins/conventions/task/maven/publish/PublicationHashGate.kt",
            "sollecitom/plugins/conventions/task/maven/publish/WritePublicationStateTask.kt",
            "sollecitom/plugins/conventions/task/maven/publish/PublicationStateRegistration.kt",
        )
    }
}
