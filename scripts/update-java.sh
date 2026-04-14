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
