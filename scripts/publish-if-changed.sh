#!/usr/bin/env bash
set -euo pipefail

props_file="gradle.properties"
state_file="build/publication-state/publication-state.properties"
original_version=$(grep '^currentVersion=' "$props_file" | cut -d= -f2-)

restore_original_version() {
    if [ -n "${original_version:-}" ] && grep -q '^currentVersion=' "$props_file"; then
        sed -i '' "s/^currentVersion=.*/currentVersion=${original_version}/" "$props_file"
    fi
}

./gradlew writePublicationState

status=$(grep '^status=' "$state_file" | cut -d= -f2-)
target_version=$(grep '^targetVersion=' "$state_file" | cut -d= -f2-)

if [ "$status" = "UNCHANGED" ]; then
    echo "Artifacts match the latest published local version. Skipping publish."
    exit 0
fi

if [ "$target_version" != "$original_version" ]; then
    sed -i '' "s/^currentVersion=.*/currentVersion=${target_version}/" "$props_file"
fi

cleanup() {
    local exit_code=$?
    if [ "$exit_code" -ne 0 ]; then
        restore_original_version
    fi
    exit "$exit_code"
}
trap cleanup EXIT

./gradlew publishToMavenLocal

trap - EXIT
echo "Published version ${target_version}"
