dependencies {
    api(libs.kotlin.gradle.plugin)
}

gradlePlugin {
    plugins {
        create("kotlin-conventions") {
            id = "sollecitom.kotlin-conventions"
            implementationClass = "sollecitom.plugins.conventions.task.kotlin.KotlinTaskConventions"
        }
    }
}