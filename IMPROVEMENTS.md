# gradle-plugins

## Overview
Reusable Gradle convention plugins providing build configuration for all projects: Kotlin compilation, test reporting, dependency management, Maven publishing, and Docker image building (Jib).

## Scorecard

| Dimension | Rating | Notes |
|-----------|--------|-------|
| Build system | A | Gradle 9.4.0, version catalog, optimized properties |
| Code quality | B+ | Clean plugin architecture, good patterns |
| Test coverage | F | Zero tests |
| Documentation | C+ | README present, no KDoc on public types |
| Dependency freshness | A | All current (Kotlin 2.3.10, Gradle 9.4.0) |
| Modularity | A | 2 components, clean separation |
| Maintainability | B | Small codebase (732 LOC), but untested |

## Structure
- 2 modules: `components/base` (14 files, 703 LOC), `components/kotlin-jvm` (1 file, 29 LOC)
- 15 Kotlin files total

## Issues
- Not proper Gradle plugins (no registered plugin IDs) — consumed via buildSrc classpath from mavenLocal
- `AggregateTestMetricsConventions` uses deprecated `BuildListener.buildFinished()` (being fixed)
- Configuration cache disabled (Palantir Git Version + Jib incompatibility)
- TODO in `RepositoryConfiguration.kt`: hardcoded GitHub packages URL
- Zero unit tests despite highly testable plugin code

## Potential Improvements
1. Register proper Gradle plugin IDs via `gradlePlugin { plugins { } }` — enables `includeBuild` consumption
2. Add Gradle TestKit tests for all convention plugins
3. Enable configuration cache (requires Palantir/Jib plugin updates or alternatives)
4. Make GitHub Packages URL configurable instead of hardcoded
5. Add KDoc for extension interfaces
6. Consider moving to precompiled script plugins (`.gradle.kts`) for readability
