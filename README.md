# Gradle plugins

A set of Gradle plugins for other projects to use.

## Requirements

1. Java 25 (neither below nor above).

## How to

### Build the project (incrementally)

```bash
just build

```

### Rebuild the project (without caches)

```bash
just rebuild

```

### Upgrade the Gradle wrapper to the latest available version

```bash
just update-gradle

```

### Update all dependencies if more recent versions exist, and remove unused ones (it will update `gradle/libs.versions.toml`)

```bash
just update-dependencies

```

## Use

In another project:

In `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}
```

In `gradle/libs.versions.toml`:

```toml
[versions]
sollecitom-gradle-plugins = "<version>"

[plugins]
sollecitom-kotlin-library-conventions = { id = "sollecitom.kotlin-library-conventions", version.ref = "sollecitom-gradle-plugins" }
```

In the root `build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.sollecitom.kotlin.library.conventions) apply false
}
```

In each module's `build.gradle.kts`:

```kotlin
plugins {
    id("sollecitom.kotlin-library-conventions")
}
```
