# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Despite the repo name, this project does **not** currently include any Spring Boot dependencies — it is a plain `jar`-packaged Maven project (generated from the `maven-archetype-quickstart` scaffold) with JUnit 5. The Spring Boot name reflects intent, not the current pom.

## Common commands

- Build: `mvn package`
- Run all tests: `mvn test`
- Run a single test class: `mvn test -Dtest=AppTest`
- Run a single test method: `mvn test -Dtest=AppTest#shouldAnswerWithTrue`
- Run the app after building: `java -cp target/example-springboot-maven-project-1.jar com.github.carloc24.springboot.App`

There is no Maven wrapper (`mvnw`); a local `mvn` installation is required. `.mvn/jvm.config` and `.mvn/maven.config` are present but empty.

## Build configuration notes

- Java release target: **17** (`maven.compiler.release` in `pom.xml`).
- JUnit 5 is imported via `junit-bom` 5.11.0; both `junit-jupiter-api` and `junit-jupiter-params` (parameterized tests) are available in test scope.
- Plugin versions are pinned in `<pluginManagement>`; prefer updating versions there rather than redeclaring plugins in `<build><plugins>`.
- Group/artifact: `com.github.carloc24.springboot:example-springboot-maven-project:1` — note the non-standard single-digit version.
