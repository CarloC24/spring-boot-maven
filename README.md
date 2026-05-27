# example-springboot-maven-project

A small Spring Boot 3.3.5 web service built with Maven and Java 17. It exposes a single REST endpoint and ships with a local PostgreSQL setup (via Docker Compose) intended for an upcoming persistence/migration layer.

> **Status:** The web app runs today. Database persistence is set up locally (Postgres container) but **not yet wired into the application** — see [Database](#database-local-postgres) and [`MIGRATION_PLAN.md`](MIGRATION_PLAN.md).

## Requirements

- **Java 17** (the compiler release target is pinned to 17)
- **Maven** — installed locally; there is no Maven wrapper (`mvnw`)
- **Docker** — only needed for the local PostgreSQL database

## Tech stack

| Concern        | Choice                                  |
|----------------|-----------------------------------------|
| Language       | Java 17                                  |
| Framework      | Spring Boot 3.3.5 (`spring-boot-starter-web`) |
| Build          | Maven (`jar` packaging)                  |
| Testing        | JUnit 5 (Jupiter, incl. parameterized)   |
| Local database | PostgreSQL 16 (Docker Compose)           |

## Getting started

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

### Try the endpoint

```bash
curl http://localhost:8080/hello
```

```json
{"message":"Hello World"}
```

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

> The application does not yet connect to this database. The planned approach —
> JDBC datasource plus Liquibase migrations run manually via the Maven plugin — is
> documented in [`MIGRATION_PLAN.md`](MIGRATION_PLAN.md).

## Project layout

```
.
├── docker-compose.yml          # Local PostgreSQL 16
├── pom.xml                     # Maven build (Spring Boot parent)
├── MIGRATION_PLAN.md           # Planned Postgres + Liquibase work
├── docs/
│   └── postgres-local-setup.md # Local DB + DBeaver guide
└── src/
    ├── main/java/com/github/carloc24/springboot/
    │   ├── App.java                          # @SpringBootApplication entry point
    │   └── controller/HelloWorldController.java
    └── test/java/com/github/carloc24/springboot/
        └── AppTest.java
```

## Coordinates

`com.github.carloc24.springboot:example-springboot-maven-project:1`
