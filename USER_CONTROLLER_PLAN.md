# User Controller Plan — JPA + Hibernate CRUD

**Branch:** `feature/users-controller`
**Status:** Draft for review (code examples included — nothing implemented yet)
**Author:** carloc (plan drafted with Claude Code)
**Decisions locked:** JPA + Hibernate · full layering (Controller → Service → Repository + DTOs) · Liquibase stays Maven-plugin-only

## Goal

Expose CRUD REST endpoints for the `users` table that `main` already created via
Liquibase (`001-create-users-table.yaml`). Persistence uses **Spring Data JPA +
Hibernate**. **Liquibase stays Maven-plugin-only** — it owns the schema; the
running app never creates or alters tables.

## Existing schema (owned by Liquibase, do not duplicate in code)

```
users
┌────────┬──────────────┬───────────────────────────┐
│ column │ type         │ constraints               │
├────────┼──────────────┼───────────────────────────┤
│ id     │ BIGINT       │ PK, autoIncrement, NOT NULL│
│ name   │ VARCHAR(255) │ NOT NULL                  │
│ age    │ INT          │ NOT NULL                  │
└────────┴──────────────┴───────────────────────────┘
```

## Critical design decision: JPA and Liquibase must not fight over the schema

This is the load-bearing decision of the whole plan. Hibernate can auto-manage DDL,
and Liquibase manages DDL. If both are "on," they collide.

```
        ┌─────────────┐   owns schema    ┌──────────┐
        │  Liquibase  │ ───────────────▶ │ Postgres │
        │ (mvn plugin)│   CREATE TABLE   │  users   │
        └─────────────┘                  └──────────┘
                                              ▲
        ┌─────────────┐  ddl-auto=validate    │
        │  Hibernate  │ ──────────────────────┘
        │   (runtime) │  READS schema, asserts entity matches,
        └─────────────┘  NEVER writes DDL
```

Rules enforced by config:
- `spring.jpa.hibernate.ddl-auto=validate` — Hibernate checks the entity maps onto
  the Liquibase-created table at startup and fails fast on drift. It never issues DDL.
- Do **not** add `liquibase-core` to runtime dependencies. Liquibase runs only via
  `mvn liquibase:update` (the existing plugin). Defensively also set
  `spring.liquibase.enabled=false` so that if Liquibase ever lands on the classpath
  transitively, the app still will not auto-run migrations.

## Layered architecture (full layering — locked)

```
        HTTP
         │  @Valid UserRequest
         ▼
  ┌──────────────────┐   invalid body → 400 (ProblemDetail)
  │  UserController  │ ◀─────────────────────────────────────┐
  │  HTTP only       │                                       │
  └────────┬─────────┘                                       │
           │ UserRequest / id                                │
           ▼                                          GlobalExceptionHandler
  ┌──────────────────┐   id not found → 404 ──────────▶ (@RestControllerAdvice)
  │   UserService    │   (ResourceNotFoundException)         │
  │  mapping + rules │                                       │
  └────────┬─────────┘                                       │
           │ User entity                                     │
           ▼                                                 │
  ┌──────────────────┐                                       │
  │  UserRepository  │  JpaRepository<User, Long>            │
  └────────┬─────────┘                                       │
           ▼                                                 │
     Hibernate → Postgres `users` ─────────────────────────-┘
```

## Endpoints

| Method | Path          | Body          | Success         | Errors                          |
|--------|---------------|---------------|-----------------|---------------------------------|
| POST   | `/users`      | `UserRequest` | 201 + Location  | 400 invalid body                |
| GET    | `/users`      | —             | 200 list        | —                               |
| GET    | `/users/{id}` | —             | 200             | 404 not found                   |
| PUT    | `/users/{id}` | `UserRequest` | 200             | 400 invalid body, 404 not found |
| DELETE | `/users/{id}` | —             | 204 No Content  | 404 not found                   |

---

# Code examples (for review — not yet implemented)

## 1. `pom.xml` — new dependencies

Add inside the existing `<dependencies>` block. Versions come from the Spring Boot
parent, so leave them unpinned.

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-validation</artifactId>
</dependency>

<!-- test scope: real Postgres for repository/integration tests -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-testcontainers</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>postgresql</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>junit-jupiter</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-test</artifactId>
  <scope>test</scope>
</dependency>
```

Note: do **not** add `liquibase-core` — it stays a Maven-plugin-only concern.

## 2. `src/main/resources/application.properties` (new file)

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/appDb
spring.datasource.username=app
spring.datasource.password=app

# Hibernate validates against the Liquibase-created schema; never writes DDL
spring.jpa.hibernate.ddl-auto=validate
# disable the open-session-in-view anti-pattern
spring.jpa.open-in-view=false
# belt-and-suspenders: app never runs migrations even if liquibase is on classpath
spring.liquibase.enabled=false
```

> WARNING: DB name is `appDb` (capital D) per `docker-compose.yml`.
> `MIGRATION_PLAN.md` / docs say `appdb`. This mismatch is real — using `appDb` here.

## 3. `User` entity — `com.github.carloc24.springboot.user.User`

```java
package com.github.carloc24.springboot.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // matches Postgres autoIncrement
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int age;

    protected User() { } // JPA requires a no-arg constructor

    public User(String name, int age) {
        this.name = name;
        this.age = age;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public int getAge() { return age; }

    public void setName(String name) { this.name = name; }
    public void setAge(int age) { this.age = age; }
}
```

Mapping must match the schema exactly — `ddl-auto=validate` fails startup otherwise.

## 4. `UserRepository` — `com.github.carloc24.springboot.user.UserRepository`

```java
package com.github.carloc24.springboot.user;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    // findAll / findById / save / deleteById come from JpaRepository — no custom queries needed
}
```

## 5. DTOs (records) — request and response

```java
package com.github.carloc24.springboot.user;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull @Min(0) Integer age) {   // Integer (not int) so a missing value is null → @NotNull fires, not 0
}
```

```java
package com.github.carloc24.springboot.user;

public record UserResponse(Long id, String name, int age) {
    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getName(), u.getAge());
    }
}
```

The JPA entity is never serialized to the API directly — DTOs decouple the wire
format from the DB schema.

## 6. `UserService` — `com.github.carloc24.springboot.user.UserService`

```java
package com.github.carloc24.springboot.user;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository repository;

    public UserService(UserRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return repository.findAll().stream().map(UserResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        return UserResponse.from(getOrThrow(id));
    }

    @Transactional
    public UserResponse create(UserRequest request) {
        User saved = repository.save(new User(request.name(), request.age()));
        return UserResponse.from(saved);
    }

    @Transactional
    public UserResponse update(Long id, UserRequest request) {
        User user = getOrThrow(id);
        user.setName(request.name());
        user.setAge(request.age());
        return UserResponse.from(repository.save(user));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(getOrThrow(id)); // explicit existence check → clean 404
    }

    private User getOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User %d not found".formatted(id)));
    }
}
```

## 7. `ResourceNotFoundException`

```java
package com.github.carloc24.springboot.user;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
```

## 8. `GlobalExceptionHandler` — RFC 7807 ProblemDetail (built into Spring Boot 3)

```java
package com.github.carloc24.springboot.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.github.carloc24.springboot.user.ResourceNotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setDetail("Validation failed");
        ex.getBindingResult().getFieldErrors().forEach(
                fe -> pd.setProperty(fe.getField(), fe.getDefaultMessage()));
        return pd;
    }
}
```

## 9. `UserController` — `com.github.carloc24.springboot.user.UserController`

```java
package com.github.carloc24.springboot.user;

import java.net.URI;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping
    public List<UserResponse> list() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserRequest request) {
        UserResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/users/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UserRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

---

# Test plan (target: every code path + error path covered)

## Coverage map

```
CODE PATHS                                  USER FLOWS / ERROR STATES
[+] UserController (@WebMvcTest)            [+] CRUD lifecycle (integration)
  ├── list / get / create / update / delete   ├── create → get → update → delete
  ├── 400 on blank name                       └── validate passes vs Liquibase schema
  ├── 400 on null/negative age
  ├── 404 on get/put/delete missing id      [+] Error states the client sees
  └── 201 sets Location header                ├── 400 ProblemDetail w/ field errors
[+] UserService (mockito)                     └── 404 ProblemDetail w/ message
  ├── create maps fields
  ├── update mutates + saves
  ├── getOrThrow → ResourceNotFound
  └── findAll maps all rows
[+] UserRepository (@DataJpaTest + Testcontainers)
  └── save/find round-trip on real Postgres
```

## Test classes

- **`UserControllerTest`** (`@WebMvcTest(UserController.class)`, `UserService` mocked) —
  each endpoint happy path; validation 400s (blank name, null age, age < 0,
  name > 255 chars); 404 paths for GET/PUT/DELETE; 201 Location header assertion.
- **`UserServiceTest`** (plain JUnit + Mockito, repository mocked) — create/update/
  delete/get behavior and `ResourceNotFoundException` on missing id.
- **`UserRepositoryTest`** (`@DataJpaTest`, `@AutoConfigureTestDatabase(replace=NONE)`,
  Testcontainers Postgres with `liquibase:update` applied) — H2 would not match
  Postgres `IDENTITY`/`BIGINT`; real Postgres is required for fidelity.
- **`UserIntegrationTest`** (`@SpringBootTest` + `@Testcontainers`,
  `MockMvc`/`TestRestTemplate`) — full HTTP→DB round-trip, proves Hibernate
  `validate` passes against the Liquibase schema.

Example slice test shape:

```java
@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired MockMvc mvc;
    @MockBean UserService service;

    @Test
    void create_returns201_withLocation() throws Exception {
        when(service.create(any())).thenReturn(new UserResponse(1L, "Ada", 36));
        mvc.perform(post("/users").contentType(APPLICATION_JSON)
                .content("""
                    {"name":"Ada","age":36}"""))
           .andExpect(status().isCreated())
           .andExpect(header().string("Location", "/users/1"));
    }

    @Test
    void create_blankName_returns400() throws Exception {
        mvc.perform(post("/users").contentType(APPLICATION_JSON)
                .content("""
                    {"name":"","age":36}"""))
           .andExpect(status().isBadRequest());
    }
}
```

---

## NOT in scope

- Liquibase running at app startup (explicit user decision — stays Maven-plugin-only).
- Pagination/sorting on `GET /users` (deferred; add `Pageable` later).
- Authn/authz on the endpoints (no security starter today).
- Unique constraint on `name` (schema allows duplicates; not requested).
- Soft delete / auditing columns (`created_at`, `updated_at`) — would need a new changeset.

## What already exists / reused

- `users` table + rollback — reused as-is, Liquibase keeps owning it.
- Postgres driver + `spring-boot-docker-compose` — reused for datasource.
- Liquibase Maven plugin — reused unchanged; app stays decoupled from it.
- `HelloWorldController` — `@RestController` style reference only.

## Open items to confirm during review

1. **Testcontainers vs docker-compose Postgres for tests** — plan uses Testcontainers
   for isolation/CI. Alternative: point tests at the running compose DB (simpler, but
   stateful and not CI-friendly).
2. **`appDb` vs `appdb` naming** — plan uses `appDb` (matches docker-compose). Worth
   fixing the docs mismatch in a follow-up so it stops causing confusion.
3. **Age validation bound** — `@Min(0)`. Add an upper bound (e.g. `@Max(150)`) if you
   want to reject nonsense ages.
