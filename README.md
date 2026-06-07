# Spring Boot Reservation System

**Java 17 · Spring Boot 3.4.4 · PostgreSQL · Redis · JPA/Hibernate · Flyway**

A REST API for managing hotel room reservations. Built as a learning project — every design decision is intentional and documented below.

---

## Quick start

Requires Docker. The app uses Spring Boot's Docker Compose integration to start PostgreSQL and Redis automatically.

```bash
./mvnw spring-boot:run
```

Swagger UI: `http://localhost:8080/swagger-ui/index.html`

---

## REST API

### Reservations — `/reservation`

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/reservation/{id}` | Get by ID. Accepts `?cacheMode=MANUAL` to read/write Redis |
| GET | `/reservation` | Search with filters: `roomId`, `userId`, `pageSize`, `pageNumber` |
| GET | `/reservation/groupby` | Filter by `userId` and `status` with pagination |
| GET | `/reservation/stats` | Request counter + monthly reservation count |
| GET | `/reservation/themostpopular/top3` | IDs of the 3 most-booked rooms |
| POST | `/reservation` | Create reservation (status auto-set to `PENDING`) |
| PUT | `/reservation/{id}` | Update reservation (only allowed when status is `PENDING`) |
| POST | `/reservation/{id}/approve` | Approve reservation (runs async) |
| DELETE | `/reservation/{id}/cancel` | Cancel reservation |
| POST | `/reservation/availability/check` | Check if a room is free for a date range |

### Users — `/hotel/users`

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/hotel/users` | List all users |
| POST | `/hotel/users/create` | Create a user |

### Response envelope

Every endpoint returns the same structure:

**Success:**
```json
{
  "success": true,
  "message": "Reservation found successfully.",
  "detailedMessage": null,
  "data": { "id": 1, "roomId": 5, "status": "PENDING" },
  "requestTime": "2026-04-25T19:47:08"
}
```

**Error:**
```json
{
  "success": false,
  "message": "Entity not found",
  "detailedMessage": "Reservation not found by id: 99",
  "data": null,
  "requestTime": "2026-04-25T19:47:08"
}
```

`ApiResponse<T>` is generic — works with any payload type. Three static factory methods handle construction: `responseOk(data, message)`, `responseOk(message)` for void responses, and `responseError(message, detailedMessage)`.

---

## Phase 1 — Java Core & Multithreading

### 1.1 Type choices in ReservationEntity

Every field type is a deliberate choice, not a default.

- **`Long` (not `long`) for id and foreign keys** — JPA uses `null` to detect whether an entity is new (no id yet = INSERT) or already persisted (id present = UPDATE). A primitive `long` can't be `null`, so Hibernate has no way to tell the difference. Using the wrapper type solves this cleanly.

- **`LocalDate` (not `Date` or `Calendar`)** — `Date` represents a point in time, not a calendar date. Reservations have a start and end date with no time component, so `LocalDate` is the right fit. It's also immutable and thread-safe, which the old `Date` class is not.

- **`ReservationStatus` stored as STRING** — storing enums as strings instead of ordinals means the DB column stays readable, and adding or reordering enum values won't silently corrupt existing data.

**Autoboxing pitfalls to watch out for:**
- Unboxing a null `Long` to `long` throws `NullPointerException` at runtime with no compiler warning
- Comparing `Long` values with `==` compares references, not values — always use `.equals()`
- Boxing/unboxing in tight loops creates unnecessary object allocation and GC pressure

---

### 1.2 Stream API

List-to-list mapping uses Stream API consistently:

```java
entities.stream().map(mapper::toDomain).toList()
```

The `groupby` endpoint filters by `userId` and `status` at the DB level via a `WHERE` clause rather than pulling all records and filtering in Java. Filtering at the query level is always faster.

---

### 1.3 Generics — ApiResponse\<T\>

The class uses a generic type parameter `<T>` so it works with any payload — `ApiResponse<ReservationDTO>`, `ApiResponse<List<ReservationDTO>>`, or `ApiResponse<Void>` for endpoints that return nothing.

**Generic wildcards — when each is used:**

| Wildcard | Meaning | Use when |
|----------|---------|----------|
| `?` | Any type | Type doesn't matter at all |
| `? extends T` | T or any subclass | Reading from a collection (Producer Extends) |
| `? super T` | T or any superclass | Writing into a collection (Consumer Super) |

PECS — Producer Extends, Consumer Super.

---

### 1.4 Multithreading

#### Thread-safe request counter

`RequestCounterService` uses `AtomicInteger` to count reservation operations since app startup.

**Why `AtomicInteger` over `synchronized`:** `counter++` is three operations — read, add, write. If two threads hit it simultaneously, both read the same value, both add 1, and both write the same result — you lose an increment. `AtomicInteger` handles this at the CPU level without blocking threads. `synchronized` would work too but forces threads to take turns, which is slower under load.

`GET /reservation/stats` returns both the in-memory request count and the number of reservations created in the current calendar month (queried from the DB).

#### Async approval with @Async and CompletableFuture

`approveReservation()` runs in a separate thread from a configured `ThreadPoolTaskExecutor`. The client gets a response immediately without waiting for the availability check and DB write to finish.

The method returns `CompletableFuture<ReservationDTO>` — a container for a result that will exist later. The controller uses `.thenApply()` to transform the result into a `ResponseEntity` once it's ready.

**ThreadPoolTaskExecutor settings:**

| Setting | Value | Reason |
|---------|-------|--------|
| `corePoolSize` | 1 | Low for local dev — tune based on load testing |
| `maxPoolSize` | 10 | Upper limit when the queue fills up |
| `queueCapacity` | 100 | Tasks wait here when all core threads are busy |
| `threadNamePrefix` | `async-reservation-` | Makes async threads identifiable in logs |

**Concurrency issues — where they could appear:**

- **Race condition** — two threads could simultaneously check room availability, both see it as free, and both approve. Addressed in Phase 2 with transaction isolation.
- **Deadlock** — not currently a risk since there's no multi-resource locking. Would become relevant if two services locked different entities in different order.
- **Visibility** — a value written in one thread isn't guaranteed to be seen by another without synchronization. `AtomicInteger` solves this for the counter; `@Transactional` handles it for DB state.

---

### 1.5 Custom exception hierarchy

Instead of throwing generic `IllegalStateException` everywhere, the codebase has a typed hierarchy:

```
ReservationException (base, extends RuntimeException)
├── ReservationNotFoundException      → 404
├── InvalidReservationStatusException → 400
└── RoomNotAvailableException         → 400
```

`GlobalExceptionHandler` catches each type and maps it to the right HTTP status. Adding a new exception type doesn't require changing the handler.

---

## Phase 2 — Database, Indexes & Migrations

### 2.1 Switching from ddl-auto=update to Flyway

The project initially used `spring.jpa.hibernate.ddl-auto=update`, where Hibernate creates and modifies tables based on `@Entity` annotations at startup. This is fine for prototyping but breaks down quickly:

- No history of schema changes
- No rollback path if a migration goes wrong
- Hibernate only adds columns, never removes them — the schema accumulates dead fields
- DDL operations skip code review entirely

Replaced with **Flyway** — schema is now managed through versioned SQL files in `src/main/resources/db/migration/`. Each change is a numbered file (`V1__init_schema.sql`, `V2__add_indexes.sql`, `V3__add_users_table.sql`), tracked in `flyway_schema_history`, applied automatically on startup.

`ddl-auto` switched to `validate` — Hibernate now only checks that `@Entity` classes match the actual schema. If they diverge, the app refuses to start. This catches drift early.

**Why Flyway over Liquibase:** Flyway uses native SQL — no XML/YAML abstraction layer. The team already knows SQL. Liquibase makes sense when you need cross-database portability or built-in rollback in the community edition. Neither applies here.

---

### V1 — Initial schema with constraints

```sql
CREATE TABLE reservations (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    room_id     BIGINT NOT NULL,
    start_date  DATE   NOT NULL,
    end_date    DATE   NOT NULL,
    status      VARCHAR(255) NOT NULL,

    CONSTRAINT reservations_status_check
        CHECK (status IN ('PENDING', 'APPROVED', 'CANCELED')),

    CONSTRAINT reservations_dates_check
        CHECK (end_date >= start_date)
);
```

**Two layers of validation:**

| Layer | What it catches |
|-------|-----------------|
| Bean Validation (`@NotNull`, `@FutureOrPresent`) on DTO | Bad input from API clients before it reaches the service |
| DB constraints (`NOT NULL`, `CHECK`) on the table | Bad data from any source — migrations, direct SQL, other services |

Application-level validation alone is fragile. Anything that bypasses the application — a migration script, a colleague running an `UPDATE` in DataGrip, a rogue cron job — can put garbage data in the DB. Constraints block this at the storage layer.

---

### V2 — Indexes targeting real query patterns

```sql
CREATE INDEX idx_reservations_user_id
    ON reservations(user_id);

CREATE INDEX idx_reservations_room_dates
    ON reservations(room_id, start_date, end_date);

CREATE INDEX idx_reservations_active
    ON reservations(start_date, end_date)
    WHERE status IN ('PENDING', 'APPROVED');
```

| Index | Purpose | Query pattern |
|-------|---------|---------------|
| `idx_reservations_user_id` | "My reservations" lookup | `WHERE user_id = ?` |
| `idx_reservations_room_dates` | Room availability check | `WHERE room_id = ? AND start_date <= ? AND end_date >= ?` |
| `idx_reservations_active` | Admin filters on active bookings | `WHERE start_date BETWEEN ? AND ? AND status IN (...)` |

**Composite index column order — ESR rule (Equality, Sort, Range):** `room_id` comes first because it's used with `=`. `start_date` and `end_date` come after because they're used with range operators. Reversing the order would break the availability query.

**Partial index trade-off:** `idx_reservations_active` only indexes rows where status is `PENDING` or `APPROVED`. Cancelled bookings are excluded entirely, making the index smaller and cheaper to maintain. The trade-off: this index only helps queries that include the same status filter.

**Why no index on `status`:** the column has only 3 distinct values. Each matches ~33% of the table. PostgreSQL's query planner correctly ignores a B-Tree index here — sequentially reading 33% of pages is faster than jumping randomly to the same number of rows through an index.

**Foreign key columns aren't auto-indexed:** PostgreSQL only auto-creates indexes for `PRIMARY KEY` and `UNIQUE` constraints. Without the manual indexes above, JOINs and "find by foreign key" queries would seq-scan the entire table.

---

#### Performance verification under load

Loaded 10M synthetic reservations and 4M users using `infra/seed.sh` (runs `generate_series` inside the Docker container). `EXPLAIN (ANALYZE, BUFFERS)` results before and after indexes:

| Query | Without index | With index | Speedup |
|-------|---------------|------------|---------|
| `WHERE user_id = 248` (~20 rows) | 5.24 ms, Seq Scan | 0.13 ms, Bitmap Heap Scan | **40×** |
| `WHERE start_date < '2026-05-01'` | — | 0.53 ms, Bitmap Index Scan | — |
| `WHERE start_date > ? AND end_date < ?` (~3% of rows) | — | Seq Scan (planner ignores index) | no benefit |

The third row is the important one: **PostgreSQL ignores the index when the result set is too large**. Around 2.79% of the table matched, which pushed the planner toward a sequential scan — random page reads for thousands of rows is slower than reading the table linearly. This is correct planner behavior, not a bug.

Indexes are not a universal "make it faster" button. Their effectiveness depends on selectivity, leftmost prefix coverage, and the planner's cost estimates. The right measurement tool is always `EXPLAIN ANALYZE`, not intuition.

---

### V3 — Users table

```sql
CREATE TABLE users (
    id          BIGSERIAL    PRIMARY KEY,
    email       VARCHAR(255) NOT NULL UNIQUE,
    first_name  VARCHAR(100) NOT NULL,
    last_name   VARCHAR(100),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE reservations
    ADD CONSTRAINT fk_reservations_user
        FOREIGN KEY (user_id) REFERENCES users(id);
```

Every reservation must reference a valid user. The FK is added after the users table exists rather than in V1 because V1 predates V3. Flyway applies migrations in order, so the constraint can't be defined before the table it references is created.

---

### 2.2 ACID, Transaction Isolation & Concurrency

#### The race condition in approveReservation

The original `approveReservation()` had no transaction boundary. Each internal operation — `findById`, `isAvailable`, `save` — ran in its own implicit transaction. This created a classic **TOCTOU (Time-of-Check to Time-of-Use)** race condition:

```
Thread A: isAvailable() → no conflicts ✅
Thread B: isAvailable() → no conflicts ✅  ← sees stale state
Thread A: save() → APPROVED
Thread B: save() → APPROVED  ← double approval
```

Both threads passed the availability check before either committed, so both saw "room is free" and both approved. Result: two overlapping reservations in `APPROVED` state.

---

#### Fix — SERIALIZABLE isolation + @Retryable

Added `@Transactional(isolation = Isolation.SERIALIZABLE)` to `approveReservation`. PostgreSQL uses **SSI (Serializable Snapshot Isolation)** which tracks read/write dependencies between concurrent transactions. When two transactions read the same range and both write into it, PostgreSQL detects the cycle and aborts one at commit time.

Confirmed by the transaction log:

```
CannotAcquireLockException: Unable to commit against JDBC Connection;
ERROR: could not serialize access due to read/write dependencies among transactions
Hint: The transaction might succeed if retried.
```

PostgreSQL itself hints at retrying — which is what `@Retryable` does:

```java
@Retryable(
    value = CannotSerializeTransactionException.class,
    maxAttempts = 3,
    backoff = @Backoff(delay = 50, multiplier = 2)
)
@Transactional(isolation = Isolation.SERIALIZABLE)
public ReservationDTO approveReservation(Long id) { ... }
```

On retry, the losing transaction tries again. By that time the first transaction has committed its `APPROVED` reservation, so the conflict check returns "room is occupied" and the retry correctly throws `InvalidReservationStatusException`.

---

#### Why @Async and @Retryable can't share a method

`@Async` and `@Retryable` on the same method conflict at the Spring proxy level:

- `@Retryable` works by catching exceptions thrown at the **call site** — it wraps the method call and retries on failure.
- `@Async` returns a `CompletableFuture` **immediately** to the caller. The actual execution happens in a separate thread, and any exception is captured inside the future — never thrown at the call site.
- Result: `@Retryable` waits for an exception that never arrives. The retry logic does nothing.

`@Transactional` doesn't have this problem — it acts inside the async thread, wrapping the method body where the method actually runs.

---

#### Architecture — splitting async dispatch from business logic

Self-invocation (`this.method()`) bypasses Spring's proxy, so the split must be between separate beans:

```
ReservationController
        ↓
AsyncApproveHandler.approveReservationAsync()   ← @Async only
        ↓ (via Spring proxy)
ReservationService.approveReservation()          ← @Retryable + @Transactional(SERIALIZABLE)
```

`AsyncApproveHandler` dispatches the call to the async thread pool. `ReservationService.approveReservation` is a plain synchronous method with the full transaction and retry logic. Called from outside its own bean, it goes through the proxy — both `@Retryable` and `@Transactional` fire correctly.

---

#### Concurrency test — forcing the race condition

A standard unit test can't reliably reproduce a race condition — threads often run sequentially by chance. The test uses two mechanisms to force genuine concurrency:

**`@MockitoSpyBean` + `CyclicBarrier`** — a spy wraps `ReservationAvailabilityService` without changing its behavior. A `doAnswer` intercept calls the real `isAvailable()`, then blocks at a `CyclicBarrier(2)`. The barrier holds both threads until both have called `isAvailable()`, then releases them simultaneously — guaranteeing both see "no conflicts" before either reaches `save()`.

```
Thread A: isAvailable() → empty ✅ → waits at barrier
Thread B: isAvailable() → empty ✅ → arrives at barrier → both released
Thread A: save() → attempts commit
Thread B: save() → attempts commit → PostgreSQL aborts one
```

**Test assertion:** `assertTrue(successCount.get() < 2)` — zero successes is also valid (both aborted in a symmetric conflict).

---

## Phase 3 — Redis Caching

### Manual caching with RedisTemplate

Two service implementations exist behind `ReservationServiceInteface`:

| Implementation | Behaviour |
|---|---|
| `NonCacheReservationService` | Always reads from PostgreSQL |
| `ManualCachingReservationService` | Reads from Redis first, falls back to PostgreSQL on miss, then writes to Redis |

The caller selects the implementation via a query parameter:

```
GET /reservation/42?cacheMode=MANUAL
```

Without the parameter, `cacheMode` defaults to `NONE_CACHE`.

`resolveReservationService()` in the controller dispatches to the right bean:

```java
private ReservationServiceInteface resolveReservationService(CacheMode cacheMode) {
    return switch (cacheMode) {
        case NONE_CACHE -> nonCacheReservationService;
        case MANUAL     -> manualCachingReservationService;
    };
}
```

**Redis key pattern:** `reservation: {id}`

**Why `RedisTemplate<String, ReservationEntity>` instead of `StringRedisTemplate`:** `StringRedisTemplate` only handles `String` values, so you'd need to serialize/deserialize manually with `ObjectMapper`. `RedisTemplate` with a configured serializer handles the conversion automatically. The template type parameter `ReservationEntity` means `.opsForValue().get(key)` returns a `ReservationEntity` directly — no casting.

---

## Rate limiting

`RateLimitFilter` runs before every request (highest filter precedence). It allows 10 requests per minute per client and returns HTTP 429 when the limit is exceeded.

**Client identity:** the filter reads the `X-API-KEY` header first. If the header is absent or blank, it falls back to the remote IP address.

**Implementation — fixed window with Redis INCR:**

```
key = "rate:{clientId}:{windowIndex}"
windowIndex = System.currentTimeMillis() / windowSizeMs
```

On each request, the counter for the current window is incremented atomically with `INCR`. When the counter first appears (value == 1), an expiry is set equal to the window size — so the key disappears automatically when the window closes. If the counter exceeds the limit, the request is rejected.

**Fixed window trade-off:** a client can send 10 requests at 00:59 and 10 more at 01:00 — effectively 20 requests in two seconds. A sliding window would prevent this but requires more Redis operations per request. For this project the fixed window is an acceptable starting point.

---

## Architecture decisions

### Why unchecked exceptions throughout

All custom exceptions extend `RuntimeException`:

- Checked exceptions would pollute every method signature with `throws ...` all the way up to the controller
- Exceptions are handled centrally in `GlobalExceptionHandler` — no reason to catch them layer by layer
- `@Transactional` rolls back on `RuntimeException` by default. Checked exceptions would require explicit `rollbackFor` configuration or transactions would commit even on failure
- Spring itself uses unchecked exceptions everywhere (`DataAccessException`, etc.) for the same reasons

### Why no DateRangeValidator utility class

The date validation logic is two lines in the service method. Extracting it into a separate class would be over-engineering at this scale. A utility class makes sense when the same logic appears in three or more places or becomes complex — neither is true here.

### Why DB-level filtering over in-memory Stream.filter()

The `groupby` endpoint filters by `userId` and `status` via a JPQL `WHERE` clause rather than loading all records and filtering in Java. Pulling data from the DB just to throw it away in the application layer makes no sense at any scale. DB-level filtering scales; in-memory filtering doesn't.

### Why DTO and Entity are separate types

`ReservationDTO` (record) is the API contract — what comes in and what goes out, with Bean Validation annotations. `ReservationEntity` is the persistence model — what maps to the table, with JPA annotations. Keeping them separate means:

- Validation rules live where input is received, not on the storage model
- Internal field changes (renaming a column, adding an audit field) don't break the API
- Sensitive or internal-only fields don't accidentally leak through the API

The mapping layer between them is a small amount of glue code, worth it.

### Why migrations live in the repository

Schema is part of the application's contract with the database. The application requires specific tables, columns, and constraints to run — that requirement belongs in source control alongside the code that depends on it.

This also means a fresh clone on a new machine boots with a single command: start Docker, run the app, Flyway creates the schema. No setup scripts to share, no `CREATE TABLE` statements sent over chat.

---

## Load testing data

`infra/seed.sh` generates test data by running SQL inside the Docker container (no local `psql` required):

```bash
bash infra/seed.sh
```

Default: 4 000 000 users, 10 000 000 reservations, inserted in batches of 500 000 via `generate_series`. Takes roughly 5–10 minutes depending on hardware.

---

## Stack

| | |
|--|--|
| Language | Java 17 |
| Framework | Spring Boot 3.4.4 |
| Database | PostgreSQL 16 |
| Cache | Redis 8 |
| ORM | JPA / Hibernate (`ddl-auto: validate`) |
| Migrations | Flyway |
| Build tool | Maven |
| Validation | Jakarta Bean Validation |
| API docs | Springdoc OpenAPI (Swagger UI) |
| Infrastructure | Docker Compose |
