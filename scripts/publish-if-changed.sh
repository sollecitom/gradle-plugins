#!/usr/bin/env bash
set -euo pipefail

props_file="gradle.properties"
state_file="build/publication-state/publication-state.properties"
tracked_state_file="publication-state.properties"
original_version=$(grep '^currentVersion=' "$props_file" | cut -d= -f2-)
project_group=$(grep '^projectGroup=' "$props_file" | cut -d= -f2-)

restore_original_version() {
    if [ -n "${original_version:-}" ] && grep -q '^currentVersion=' "$props_file"; then
        sed -i '' "s/^currentVersion=.*/currentVersion=${original_version}/" "$props_file"
    fi
}

write_tracked_state() {
    local temp_file
    local maven_root
    local artifact_lines
    local artifact_count
    temp_file=$(mktemp "${tracked_state_file}.tmp.XXXXXX")
    maven_root="${HOME}/.m2/repository/$(printf '%s' "$project_group" | tr '.' '/')"
    artifact_lines=$(
        find . -path './build/libs/*' -prune -o -path '*/build/libs/*.jar' -type f -print | sort | while read -r build_jar; do
            local_file_name=$(basename "$build_jar")

            if [[ "$local_file_name" == *-"${target_version}.jar" ]]; then
                artifact_id=${local_file_name%"-${target_version}.jar"}
                classifier=""
            elif [[ "$local_file_name" == *-"${target_version}"-*.jar ]]; then
                artifact_id=${local_file_name%%-"${target_version}"-*}
                classifier=${local_file_name#"$artifact_id-$target_version-"}
                classifier=${classifier%.jar}
            else
                continue
            fi

            published_jar="${maven_root}/${artifact_id}/${target_version}/${local_file_name}"
            [ -f "$published_jar" ] || continue

            if [ -n "$classifier" ]; then
                identity="${project_group}:${artifact_id}:${classifier}@jar"
            else
                identity="${project_group}:${artifact_id}@jar"
            fi

            sha256=$(shasum -a 256 "$published_jar" | awk '{print $1}')
            printf '%s|%s\n' "$identity" "$sha256"
        done | sort -u
    )
    artifact_count=$(printf '%s\n' "$artifact_lines" | sed '/^$/d' | wc -l | tr -d ' ')
    {
        echo "publishedVersion=${target_version}"
        echo "artifactCount=${artifact_count}"
        index=0
        while IFS='|' read -r identity sha256; do
            [ -n "${identity:-}" ] || continue
            index=$((index + 1))
            echo "artifact.${index}.identity=${identity}"
            echo "artifact.${index}.sha256=${sha256}"
        done <<< "$artifact_lines"
    } > "$temp_file"
    mv "$temp_file" "$tracked_state_file"
}

./gradlew writePublicationState

status=$(grep '^status=' "$state_file" | cut -d= -f2-)
target_version=$(grep '^targetVersion=' "$state_file" | cut -d= -f2-)

if [ "$status" = "UNCHANGED" ]; then
    echo "Artifacts match the tracked published state. Skipping publish."
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

write_tracked_state
trap - EXIT
echo "Published version ${target_version}"
