# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

A Spring Boot 3.3.5 web application built with Maven on Java 17. It uses the `spring-boot-starter-parent` and `spring-boot-starter-web`, and currently exposes a single REST endpoint:

- `GET /hello` → JSON `{"message":"Hello World"}` (see `HelloWorldController`).

The entry point is `App` (`@SpringBootApplication`). Packaging is `jar`.

A local PostgreSQL 16 setup exists (`docker-compose.yml`) for upcoming persistence work, but **the application does not yet connect to a database** — there are no JDBC/JPA/Liquibase dependencies in `pom.xml` and no `src/main/resources/application.*`. The planned persistence + Liquibase migration approach is documented in `MIGRATION_PLAN.md`, and `docs/postgres-local-setup.md` covers running Postgres locally.

## Common commands

- Build: `mvn package`
- Run all tests: `mvn test`
- Run a single test class: `mvn test -Dtest=AppTest`
- Run a single test method: `mvn test -Dtest=AppTest#shouldAnswerWithTrue`
- Run the app (dev): `mvn spring-boot:run` — starts on `http://localhost:8080`
- Run the packaged jar: `java -jar target/example-springboot-maven-project-1.jar`
- Start local Postgres: `docker compose up -d` (stop with `docker compose down`, wipe data with `docker compose down -v`)

There is no Maven wrapper (`mvnw`); a local `mvn` installation is required. `.mvn/jvm.config` and `.mvn/maven.config` are present but empty.

## Build configuration notes

- Java release target: **17** (`maven.compiler.release` in `pom.xml`).
- Spring Boot version comes from the `spring-boot-starter-parent` 3.3.5; dependency versions are managed by the parent — avoid pinning versions for Spring-managed dependencies.
- JUnit 5 is imported via `junit-bom` 5.11.0; both `junit-jupiter-api` and `junit-jupiter-params` (parameterized tests) are available in test scope.
- `start-class` property points at `com.github.carloc24.springboot.App`; the `spring-boot-maven-plugin` is configured (in `<pluginManagement>`) with the `local` and `dev` profiles (`app.profiles` property).
- Plugin versions are pinned in `<pluginManagement>`; prefer updating versions there rather than redeclaring plugins in `<build><plugins>`.
- Group/artifact: `com.github.carloc24.springboot:example-springboot-maven-project:1` — note the non-standard single-digit version.

## Local database (Docker Compose)

`docker-compose.yml` runs PostgreSQL 16 with: database `appDb`, user `app`, password `app`, on `localhost:5432`, with a named volume `postgres_data` for persistence. Note the DB name `appDb` differs from the lowercase `appdb` used in `MIGRATION_PLAN.md` and `docs/postgres-local-setup.md`.
