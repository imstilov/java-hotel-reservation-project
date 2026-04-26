# Spring Boot Reservation System

**Java 17 · Spring Boot 3.4.4 · PostgreSQL · JPA/Hibernate · Flyway**

A REST API for managing room reservations. Built as a personal learning project — every design decision here is intentional and documented below.

---

## REST API

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/reservation/{id}` | Get reservation by ID |
| GET | `/reservation` | Search with filters (roomId, userId, pagination) |
| GET | `/reservation/groupby` | Filter by userId and status with pagination |
| GET | `/reservation/stats` | Request counter + monthly reservation count |
| POST | `/reservation` | Create reservation (status auto-set to PENDING) |
| PUT | `/reservation/{id}` | Update reservation (PENDING only) |
| POST | `/reservation/{id}/approve` | Approve reservation (async) |
| DELETE | `/reservation/{id}/cancel` | Cancel reservation |
| POST | `/reservation/availability/check` | Check room availability for a date range |

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

Every endpoint returns a consistent response envelope. The client always gets the same structure regardless of what it called.

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

The class uses a generic type parameter `<T>` so it works with any payload — `ApiResponse<Reservation>`, `ApiResponse<List<Reservation>>`, or `ApiResponse<Void>` for endpoints that return nothing.

Three static factory methods handle construction:

- `responseOk(T data, String message)` — successful response with payload
- `responseOk(String message)` — successful response, no payload (cancel/delete)
- `responseError(String message, String detailedMessage)` — error with a short user-facing message and a detailed one for debugging

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

`RequestCounterService` uses `AtomicInteger` to count reservation creations since app startup. It increments on every successful `createReservation()` call.

**Why `AtomicInteger` over `synchronized`:** `counter++` is three operations — read, add, write. If two threads hit it simultaneously, both read the same value, both add 1, and both write the same result — you lose an increment. `AtomicInteger` handles this at the CPU level without blocking threads. `synchronized` would work too but forces threads to take turns, which is slower under load.

`GET /reservation/stats` returns both the in-memory request count and the number of reservations created in the current calendar month (queried directly from the DB).

#### Async approval with @Async and CompletableFuture

`approveReservation()` runs in a separate thread from a configured `ThreadPoolTaskExecutor`. The client gets a response immediately without waiting for the availability check and DB write to complete.

The method returns `CompletableFuture<Reservation>` — a container for a result that will exist later. The controller uses `.thenApply()` to transform the reservation into a `ResponseEntity` once it's ready.

**ThreadPoolTaskExecutor settings:**

| Setting | Value | Reason |
|---------|-------|--------|
| `corePoolSize` | 1 | Low for local dev — tuned based on load testing in production |
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

`GlobalExceptionHandler` catches `ReservationException` and its subclasses. Adding a new exception type doesn't require touching the handler.

---

## Phase 2 — Database, Indexes & Migrations

### 2.1 Switching from ddl-auto=update to Flyway

The project initially used `spring.jpa.hibernate.ddl-auto=update`, where Hibernate creates and modifies tables based on `@Entity` annotations at startup. This is convenient for prototyping but unacceptable for any environment beyond a local sandbox:

- No history of schema changes — impossible to know who added what and when
- No rollback path if a migration goes wrong
- Hibernate only adds columns, never removes them — the schema accumulates dead fields
- Running on a different machine produces unpredictable results depending on which entities are in classpath
- DDL operations skip code review entirely

Replaced with **Flyway** — schema is now managed through versioned SQL files in `src/main/resources/db/migration/`. Each change is a numbered file (`V1__init_schema.sql`, `V2__add_indexes.sql`), tracked in `flyway_schema_history`, applied automatically on startup.

`ddl-auto` switched to `validate` — Hibernate now only checks that `@Entity` classes match the actual schema. If they diverge, the app fails to start. This catches drift early instead of in production.

**Why Flyway over Liquibase:** Flyway uses native SQL — no XML/YAML abstraction layer to learn. The team already knows SQL, so migrations are immediately readable. Liquibase shines when you need cross-database portability (PostgreSQL + Oracle + MSSQL from one codebase) or built-in rollback in the community edition. Neither applies here.

---

### 2.2 V1 — Initial schema with constraints

The initial migration creates the `reservations` table with explicit constraints rather than relying solely on application-level validation:

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

**Defense in depth — two layers of validation:**

| Layer | What it catches |
|-------|-----------------|
| Bean Validation (`@NotNull`, `@FutureOrPresent`) on `Reservation` DTO | Bad input from API clients before it reaches the service |
| DB constraints (`NOT NULL`, `CHECK`) on `reservations` table | Bad data from any source — direct SQL, other services, manual DBA edits, bugs in the Java code |

Application-level validation alone is fragile. Anything that bypasses the application — a migration script, a colleague running an UPDATE in DataGrip, a rogue cron job — can put garbage data in the DB. Constraints make this impossible at the storage layer.

The `reservations_dates_check` constraint also doubles as a safety net against a class of bugs where `endDate` and `startDate` get swapped in the service code.

---

### 2.3 V2 — Indexes targeting real query patterns

Three indexes were added based on actual query patterns rather than guessing:

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
| `idx_reservations_active` | Admin filters on active bookings | `WHERE start_date BETWEEN ? AND ? AND status IN ('PENDING', 'APPROVED')` |

**Composite index column order — ESR rule (Equality, Sort, Range):** `room_id` comes first because it's used with `=`. `start_date` and `end_date` come after because they're used with range operators. Reversing the order would make the index nearly useless for the availability query.

**Partial index trade-off:** `idx_reservations_active` only contains rows where status is `PENDING` or `APPROVED`. Cancelled bookings are excluded entirely. This makes the index smaller (only ~66% of rows are indexed) and faster to maintain — INSERTs and UPDATEs on cancelled bookings don't touch this index. The trade-off is that the index only helps queries that include the same status filter; queries without it fall back to a sequential scan.

**Why no index on `status`:** the column has only 3 distinct values across 100k rows (`PENDING`, `APPROVED`, `CANCELED`). Each value matches ~33% of the table. PostgreSQL's planner correctly ignores any B-Tree index here — sequentially reading 33% of pages is faster than jumping randomly to the same number of rows through an index. Low-cardinality columns rarely benefit from indexing.

**Foreign key columns aren't auto-indexed:** PostgreSQL only auto-creates indexes for `PRIMARY KEY` and `UNIQUE` constraints. Even though `user_id` and `room_id` reference other tables conceptually, no index is created automatically. This is a common gotcha — without the manual indexes above, JOINs and "find by foreign key" queries would seq-scan the entire table.

---

### 2.4 Performance verification under load

Loaded 100k synthetic reservations using `generate_series` in PostgreSQL. The data is spread across 180 unique rooms (floors 1–9, 20 rooms per floor — `101..120, 201..220, ..., 901..920`) and ~5000 unique users, with start dates spanning a 2-year window.

`EXPLAIN (ANALYZE, BUFFERS)` results, before and after indexes:

| Query | Without index | With index | Speedup |
|-------|---------------|------------|---------|
| `WHERE user_id = 248` (selective, ~21 rows) | 5.24 ms, Seq Scan, 944 pages | 0.13 ms, Bitmap Heap Scan, 26 pages | **40×** |
| `WHERE start_date < '2026-05-01'` (selective) | — | 0.53 ms, Bitmap Index Scan via composite | — |
| `WHERE start_date > ? AND end_date < ?` (~3% of rows) | — | 5.32 ms, **Seq Scan** | (no benefit) |

The third row reveals an important nuance: **PostgreSQL ignores the index when the result set is too large**. Around 2.79% of the table matched, which pushed the planner toward a sequential scan — random page reads for thousands of rows would be slower than reading the table linearly. This is the planner working correctly, not a bug.

**Practical takeaway: indexes are not a universal "make it faster" button.** Their effectiveness depends on selectivity, leftmost prefix coverage, and the query planner's cost estimates based on table statistics. The right tool for measuring this is always `EXPLAIN ANALYZE`, not intuition.

---

### 2.5 Entity-schema synchronization

Switching to `ddl-auto=validate` requires `ReservationEntity` to exactly match the table schema. Updated all `@Column` annotations to declare nullability explicitly:

```java
@Column(name = "user_id", nullable = false)
private Long userId;

@Column(name = "room_id", nullable = false)
private Long roomId;

@Column(name = "start_date", nullable = false)
private LocalDate startDate;

@Column(name = "end_date", nullable = false)
private LocalDate endDate;

@Enumerated(EnumType.STRING)
@Column(name = "status", nullable = false, length = 255)
private ReservationStatus status;
```

Without this, a misalignment between Java and the DB silently allows null values where the DB wouldn't accept them — Hibernate generates an INSERT with explicit nulls, and the DB rejects it with a less obvious error. With `nullable = false` declared, Hibernate fails earlier with a clear message.

---

## Architecture decisions

### Why unchecked exceptions throughout

All custom exceptions extend `RuntimeException`. There are a few reasons this makes sense for a Spring web app:

- Checked exceptions would pollute every method signature with `throws ...` all the way up to the controller
- Exceptions are handled centrally in `GlobalExceptionHandler` — no reason to catch them layer by layer
- `@Transactional` rolls back on `RuntimeException` by default. Checked exceptions would require explicit `rollbackFor` configuration or transactions would commit even on failure
- Spring itself uses unchecked exceptions everywhere (`DataAccessException`, etc.) for the same reasons

### Why no DateRangeValidator utility class

The date validation logic is two lines in the service method. Extracting it into a separate class would be over-engineering (YAGNI). A utility class makes sense when the same logic appears in 3+ places or becomes more complex — neither is true here.

### Why DB-level filtering over in-memory Stream.filter()

The `groupby` endpoint filters by `userId` and `status` via a JPQL `WHERE` clause rather than loading all records and filtering in Java. Pulling unnecessary data from the DB just to throw it away in the application layer makes no sense. DB-level filtering scales; in-memory filtering doesn't.

### Why DTO and Entity are separate types

`Reservation` (record) is the API contract — what comes in and what goes out, with Bean Validation annotations. `ReservationEntity` is the persistence model — what maps to the table, with JPA annotations. Keeping them separate means:

- Validation rules live where input is received, not on the storage model
- Internal field changes (renaming a column, adding an audit field) don't break the API
- Sensitive or internal-only fields don't accidentally leak through the API just because they're on the entity

The mapping layer between them is trivial and worth the small amount of glue code.

### Why migrations live in the repository

Schema is part of the application's contract with the database. The application requires specific tables, columns, and constraints to function — that requirement belongs in source control alongside the code that depends on it. A repository that builds without its schema is incomplete, the same way a project without its `pom.xml` is incomplete.

This also means a fresh clone on a new machine boots with a single command: start PostgreSQL, run the app, Flyway creates the schema. No setup scripts to share, no Slack messages with `CREATE TABLE` statements.

---

## Stack

| | |
|--|--|
| Language | Java 17 |
| Framework | Spring Boot 3.4.4 |
| Database | PostgreSQL |
| ORM | JPA / Hibernate (ddl-auto: validate) |
| Migrations | Flyway |
| Build tool | Maven |
| Validation | Jakarta Bean Validation |
