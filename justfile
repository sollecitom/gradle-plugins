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

license-audit:
    bash ../scripts/run-license-audit.sh gradle-plugins

generate-sbom:
    bash ../scripts/run-generate-sbom.sh gradle-plugins

cleanup:
    bash ../scripts/cleanup-maven-local.sh --repo-root . --keep 5 --max-age-days 30

update-internal-dependencies:
    @:

rebuild:
    ./gradlew clean build --refresh-dependencies --rerun-tasks

publish:
    ./scripts/publish-if-changed.sh

update-dependencies:
    ./gradlew versionCatalogUpdate

@update-gradle:
    ./scripts/update-gradle.sh

@update-java:
    just -f ../justfile update-java-workspace

update-all:
    just update-internal-dependencies && just update-dependencies && just update-gradle

workflow +steps:
    bash ../scripts/run-just-workflow.sh {{steps}}
