# example-springboot-maven-project

A small Spring Boot 3.3.5 web service built with Maven and Java 17. It exposes a REST endpoint, ships with a local PostgreSQL setup (via Docker Compose), and manages its schema with Liquibase migrations.

> **Status:** The web app runs today. A local PostgreSQL container and Liquibase migrations are in place (a `users` table is defined), but the application is **not yet wired to the database at runtime** — there is no JPA/datasource layer yet. JPA-backed persistence and a `User` CRUD controller are planned; see [`USER_CONTROLLER_PLAN.md`](USER_CONTROLLER_PLAN.md) and [`MIGRATION_PLAN.md`](MIGRATION_PLAN.md).

## Requirements

| Tool   | Version            | Notes                                                  |
|--------|--------------------|--------------------------------------------------------|
| Java   | 17                 | Compiler release is pinned to 17 in `pom.xml`          |
| Maven  | 3.6+               | Installed locally — there is **no** Maven wrapper (`mvnw`) |
| Docker | any recent version | Only needed for the local PostgreSQL database          |

## Tech stack

| Concern         | Choice                                          |
|-----------------|-------------------------------------------------|
| Language        | Java 17                                          |
| Framework       | Spring Boot 3.3.5 (`spring-boot-starter-web`)    |
| Build           | Maven (`jar` packaging)                          |
| Testing         | JUnit 5 (Jupiter, incl. parameterized)           |
| Local database  | PostgreSQL 16 (Docker Compose)                   |
| Migrations      | Liquibase 4.29 (Maven plugin)                    |

## Getting started

The fastest path to a running service:

```bash
git clone <your-fork-url>
cd spring-boot-maven
mvn spring-boot:run
```

Then in another terminal:

```bash
curl http://localhost:8080/hello
# {"message":"Hello World"}
```

### Build

```bash
mvn package
```

### Run the application

```bash
mvn spring-boot:run
```

Or run the packaged jar after building:

```bash
java -jar target/example-springboot-maven-project-1.jar
```

The app starts on `http://localhost:8080`.

> **Heads up — automatic Postgres:** this project includes the `spring-boot-docker-compose`
> dependency. When you run the app with Docker available, Spring Boot will automatically
> start the `docker-compose.yml` Postgres container (and stop it on shutdown). The app still
> runs without Docker today because nothing connects to the database at runtime yet — but
> once Docker is present you'll see Compose lifecycle logs during startup.

### Try the endpoint

```bash
curl http://localhost:8080/hello
```

```json
{"message":"Hello World"}
```

| Method | Path     | Response                    |
|--------|----------|-----------------------------|
| `GET`  | `/hello` | `{"message":"Hello World"}` |

The endpoint is defined in
[`HelloWorldController`](src/main/java/com/github/carloc24/springboot/controller/HelloWorldController.java).

## Testing

```bash
# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=AppTest

# Run a single test method
mvn test -Dtest=AppTest#shouldAnswerWithTrue
```

## Database (local Postgres)

A [`docker-compose.yml`](docker-compose.yml) runs PostgreSQL 16 for local development:

```bash
docker compose up -d        # start Postgres
docker compose ps           # check health
docker compose down         # stop (keeps data)
docker compose down -v      # stop and wipe data
```

Default connection settings:

| Setting   | Value       |
|-----------|-------------|
| Host      | `localhost` |
| Port      | `5432`      |
| Database  | `appDb`     |
| Username  | `app`       |
| Password  | `app`       |

For a step-by-step guide (including connecting from DBeaver and troubleshooting),
see [`docs/postgres-local-setup.md`](docs/postgres-local-setup.md).

## Database migrations (Liquibase)

Schema changes are managed with Liquibase. The master changelog is
[`db.changelog-master.yaml`](src/main/resources/db/changelog/db.changelog-master.yaml),
and individual change sets live under
[`db/changelog/changes/`](src/main/resources/db/changelog/changes/). The first migration
creates a `users` table (`id`, `name`, `age`).

Migrations run via the Liquibase Maven plugin against the database configured in
[`liquibase.properties`](src/main/resources/liquibase.properties) (the local Postgres above).
Make sure Postgres is running first (`docker compose up -d`), then:

```bash
mvn liquibase:update     # apply pending change sets
mvn liquibase:status     # show unapplied change sets
mvn liquibase:rollback -Dliquibase.rollbackCount=1   # roll back the last change set
```

## Project layout

```
.
├── docker-compose.yml          # Local PostgreSQL 16
├── pom.xml                     # Maven build (Spring Boot parent)
├── MIGRATION_PLAN.md           # Planned Postgres + Liquibase work
├── USER_CONTROLLER_PLAN.md     # Planned User CRUD controller (JPA + Hibernate)
├── docs/
│   └── postgres-local-setup.md # Local DB + DBeaver guide
└── src/
    ├── main/
    │   ├── java/com/github/carloc24/springboot/
    │   │   ├── App.java                          # @SpringBootApplication entry point
    │   │   └── controller/HelloWorldController.java
    │   └── resources/
    │       ├── liquibase.properties              # Liquibase DB connection
    │       └── db/changelog/                      # Liquibase changelogs
    └── test/java/com/github/carloc24/springboot/
        └── AppTest.java
```

## Coordinates

`com.github.carloc24.springboot:example-springboot-maven-project:1`
