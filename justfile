#!/usr/bin/env just --justfile

set quiet

reset-all:
    git fetch origin && git reset --hard origin/main && git clean -f -d

push:
    git add -A && (git diff --quiet HEAD || git commit -am "WIP") && git push origin main

pull:
    git pull

build:
    ./gradlew build

rebuild:
    ./gradlew clean build --refresh-dependencies --rerun-tasks

publish:
    ./gradlew publishToMavenLocal

update-dependencies:
    ./gradlew versionCatalogUpdate

@update-gradle:
    #!/usr/bin/env bash
    set -euo pipefail
    ./gradlew wrapper --gradle-version latest --distribution-type all
    DIST_URL=$(grep distributionUrl gradle/wrapper/gradle-wrapper.properties | cut -d= -f2 | sed 's/\\//g')
    CHECKSUM=$(curl -sL "${DIST_URL}.sha256")
    sed -i '' "s/distributionSha256Sum=.*/distributionSha256Sum=${CHECKSUM}/" gradle/wrapper/gradle-wrapper.properties
    echo "Updated wrapper checksum: ${CHECKSUM}"

@update-java:
    #!/usr/bin/env bash
    set -euo pipefail
    echo "Updating Temurin JDK via Homebrew..."
    brew upgrade --cask temurin || brew install --cask temurin
    echo "Installed JDK version:"
    /usr/libexec/java_home -V 2>&1
    echo ""
    echo "If the version changed, update:"
    echo "  components/base/src/main/kotlin/sollecitom/plugins/Plugins.kt (VERSION_XX)"
    echo "  components/kotlin-jvm/src/main/kotlin/.../KotlinTaskConventions.kt (JVM_XX)"
    echo "  All projects: gradle.properties (dockerBaseImageParam eclipse-temurin:XX-alpine)"

update-all:
    just update-dependencies && just update-gradle
