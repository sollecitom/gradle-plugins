dependencies {
    api(libs.semver4j)
    api(libs.nl.littlerobots.version.catalog.update)
    api(libs.kotlin.gradle.plugin)
    api(project(":kotlin-jvm"))
    api(libs.jib.gradle.plugin)
}

gradlePlugin {
    plugins {
        create("dependency-update-conventions") {
            id = "sollecitom.dependency-update-conventions"
            implementationClass = "sollecitom.plugins.conventions.task.dependency.update.DependencyUpdateConvention"
        }
        create("minimum-dependency-version-conventions") {
            id = "sollecitom.minimum-dependency-version-conventions"
            implementationClass = "sollecitom.plugins.conventions.task.dependency.version.MinimumDependencyVersionConventions"
        }
        create("test-conventions") {
            id = "sollecitom.test-conventions"
            implementationClass = "sollecitom.plugins.conventions.task.test.TestTaskConventions"
        }
        create("aggregate-test-metrics-conventions") {
            id = "sollecitom.aggregate-test-metrics-conventions"
            implementationClass = "sollecitom.plugins.conventions.task.test.AggregateTestMetricsConventions"
        }
        create("maven-publish-conventions") {
            id = "sollecitom.maven-publish-conventions"
            implementationClass = "sollecitom.plugins.conventions.task.maven.publish.MavenPublishConvention"
        }
        create("jib-docker-build-conventions") {
            id = "sollecitom.jib-docker-build-conventions"
            implementationClass = "sollecitom.plugins.conventions.task.jib.JibDockerBuildConvention"
        }
        create("container-based-service-test-conventions") {
            id = "sollecitom.container-based-service-test-conventions"
            implementationClass = "sollecitom.plugins.conventions.task.container.test.ContainerBasedServiceTestConvention"
        }
        create("kotlin-library-conventions") {
            id = "sollecitom.kotlin-library-conventions"
            implementationClass = "sollecitom.plugins.conventions.KotlinLibraryConventions"
        }
        create("security-scan-conventions") {
            id = "sollecitom.security-scan-conventions"
            implementationClass = "sollecitom.plugins.conventions.task.security.scan.SecurityScanConvention"
        }
    }
}