# Plan: Local Postgres + Liquibase migration for `User` table

## Context

The project (`spring-boot-maven`) is a Spring Boot 3.3.5 / Java 17 web app with no persistence layer yet. The user wants to:

1. Run Postgres locally (via Docker Compose).
2. Create a `users` table with columns `id`, `name`, `age` using the **industry-standard** migration approach for Spring Boot + Maven.

Decisions made with the user:
- **Migration tool:** Liquibase, but **run manually via the Liquibase Maven plugin** — not on Spring Boot startup. The user wants full control over when migrations apply.
- **Local Postgres:** Docker Compose (Spring Boot 3.1+ auto-detects `compose.yaml` via `spring-boot-docker-compose`, no manual connection wiring needed for the app at runtime).
- **No entity/repo scaffold** — migration only.

The CLAUDE.md in the repo is stale (claims no Spring Boot deps); plan reflects the actual pom.

## Changes

### 1. `pom.xml` — add dependencies and the Liquibase Maven plugin

**Dependencies** (versions managed by `spring-boot-starter-parent`):
- `org.springframework.boot:spring-boot-starter-jdbc` — gives the app a `DataSource` so it can talk to Postgres at runtime (no JPA per "migration only" choice).
- `org.postgresql:postgresql` (scope `runtime`) — JDBC driver, used both by the app and by the Liquibase Maven plugin.
- `org.springframework.boot:spring-boot-docker-compose` (scope `runtime`, `optional=true`) — auto-starts `compose.yaml` when the app runs.

**Deliberately NOT added:** `org.liquibase:liquibase-core` and `spring-boot-starter` Liquibase auto-config. Keeping Liquibase off the app classpath guarantees migrations cannot run on startup — only the Maven plugin can apply them.

**Plugin** (under `<build><plugins>`): `org.liquibase:liquibase-maven-plugin` (pin a version, e.g. `4.29.2`) configured with:
- `changeLogFile`: `src/main/resources/db/changelog/db.changelog-master.yaml`
- `propertyFile`: `src/main/resources/liquibase.properties` (keeps URL/creds out of `pom.xml`)
- `propertyFileWillOverride`: `true`
- Postgres driver declared as a `<dependency>` inside the plugin block so `mvn liquibase:*` has the driver on its classpath.

### 2. `compose.yaml` (new, project root)
Single `postgres:16` service:
- container name `spring-boot-maven-postgres`
- env: `POSTGRES_DB=appdb`, `POSTGRES_USER=app`, `POSTGRES_PASSWORD=app`
- port `5432:5432`
- named volume `pgdata` for persistence

Spring Boot's docker-compose support discovers this automatically — no JDBC URL needed in properties for local runs.

### 3. `src/main/resources/application.yml` (new)
- No `spring.liquibase.*` section at all (Liquibase isn't on the runtime classpath, so nothing to disable — but if a future contributor adds it, also set `spring.liquibase.enabled: false` as a belt-and-suspenders guard).
- Leave datasource unset for local (docker-compose module fills it). Add commented example for non-Docker runs:
  ```
  # spring.datasource.url: jdbc:postgresql://localhost:5432/appdb
  # spring.datasource.username: app
  # spring.datasource.password: app
  ```

### 3b. `src/main/resources/liquibase.properties` (new)
Used by the Maven plugin only (not loaded by Spring). Points at the same local Postgres that docker-compose runs:
```
url=jdbc:postgresql://localhost:5432/appdb
username=app
password=app
driver=org.postgresql.Driver
```
For real environments, override via `mvn liquibase:update -Dliquibase.url=... -Dliquibase.username=... -Dliquibase.password=...` or a separate properties file per env.

### 4. Liquibase changelog files (industry-standard layout)

**`src/main/resources/db/changelog/db.changelog-master.yaml`** — master file, only includes versioned changelogs (keeps master stable, never edited for schema changes):
```yaml
databaseChangeLog:
  - include:
      file: db/changelog/changes/001-create-users-table.yaml
```

**`src/main/resources/db/changelog/changes/001-create-users-table.yaml`** — the actual change:
- `changeSet` id `001-create-users-table`, author `carloc`
- `createTable` `users` with:
  - `id` `BIGINT`, PK, auto-increment (generated identity), not null
  - `name` `VARCHAR(255)`, not null
  - `age` `INT`, nullable
- `rollback` block dropping the table (Liquibase best practice — every changeset should be rollback-safe).

Note: table named `users` (plural, lowercased) because `user` is a reserved word in Postgres.

### 5. `.gitignore` — confirm `target/` ignored (already is via Maven defaults; no change needed unless missing).

## Files touched

- `pom.xml` (edit — deps + Liquibase Maven plugin)
- `compose.yaml` (new)
- `src/main/resources/application.yml` (new)
- `src/main/resources/liquibase.properties` (new)
- `src/main/resources/db/changelog/db.changelog-master.yaml` (new)
- `src/main/resources/db/changelog/changes/001-create-users-table.yaml` (new)

## Why this is the "industry standard" shape (with manual control)

- **Liquibase Maven plugin, not the Spring Boot starter** → migrations are an explicit, operator-driven step (`mvn liquibase:update`), the same model used in production CI/CD pipelines where DB changes are gated separately from app deploys.
- **Liquibase not on the runtime classpath** → it's impossible for app startup to apply migrations.
- **Master changelog + `include` per change file** → each schema change is an immutable, versioned, reviewable file. Never edit a committed changeset — add a new one.
- **YAML changelogs** → DB-agnostic, diff-friendly, the most common modern Liquibase format (XML is legacy; SQL-format works but loses rollback metadata).
- **`compose.yaml` at repo root + `spring-boot-docker-compose`** → one-command local dev for the app. Postgres is also reachable from your host on `localhost:5432` so the Maven plugin can talk to it.

## Typical workflow

1. `docker compose up -d postgres` (or just `mvn spring-boot:run` once, which boots the container via the docker-compose module — then `Ctrl+C` the app, container keeps running).
2. `mvn liquibase:update` — apply pending migrations. **You** decide when.
3. `mvn spring-boot:run` — start the app against the now-migrated DB.

Other useful plugin goals you now have: `liquibase:status`, `liquibase:rollback`, `liquibase:validate`, `liquibase:dropAll`, `liquibase:updateSQL` (prints SQL without executing — great for review).

## Verification

1. `docker --version` to confirm Docker is available.
2. `mvn clean package` — build succeeds with new deps.
3. `docker compose up -d postgres` — Postgres container is healthy.
4. `mvn liquibase:status` — expect output listing `001-create-users-table` as pending.
5. `mvn liquibase:update` — expect "ChangeSet 001-create-users-table ran successfully".
6. Inspect the DB:
   ```
   docker exec -it spring-boot-maven-postgres psql -U app -d appdb -c "\d users"
   docker exec -it spring-boot-maven-postgres psql -U app -d appdb -c "SELECT * FROM databasechangelog;"
   ```
   Expect the `users` table with `id/name/age` and a Liquibase tracking row for `001-create-users-table`.
7. `mvn spring-boot:run` — app starts; logs must NOT show any Liquibase activity (proves migrations are not auto-running). `curl http://localhost:8080/hello` still works.
8. Rerun `mvn liquibase:update` — should report 0 changesets to apply (idempotent).
9. Optional: `mvn liquibase:rollbackCount -Dliquibase.rollbackCount=1` then re-`update` to verify the rollback block in the changeset works.
