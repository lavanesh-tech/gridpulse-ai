# Architecture Decision Records

## ADR-001: Modular monolith with Spring Modulith
**Decision:** Single Spring Boot app; modules verified by Spring Modulith tests.
**Why:** Strong boundaries without distributed-systems overhead; scheduler needs transactional consistency.
**Trade-off:** Modules scale together; extraction to services later would need event contracts (Modulith events help).

## ADR-002: Spring Boot 3.5 line
**Decision:** Spring Boot 3.5.x on Java 21.
**Why:** Mature, well-documented, compatible with Spring AI 1.x and Spring Modulith 1.4.
**Revisit:** Upgrade to Boot 4 once Spring AI and other dependencies are stable on it.

## ADR-003: Flyway owns the schema
**Decision:** `ddl-auto: validate`; all changes via versioned SQL migrations.
**Why:** Reviewable, repeatable schema history; Hibernate cannot silently change production tables.

## ADR-004: Value objects for physical units
**Decision:** `PowerKw`, `EnergyKwh`, `StateOfCharge`, `TimeWindow` as immutable records with validation.
**Why:** Prevents unit mix-ups (kW vs kWh) and invalid states at compile/construct time.

## ADR-005: Half-open time windows
**Decision:** Sessions use `[start, end)`.
**Why:** Back-to-back sessions on one charger (one ends 10:00, next starts 10:00) are valid, not overlaps.

## ADR-006: Optimistic locking for schedules and reservations
**Decision:** `version` columns + unique/exclusion constraints in Postgres (M2–M4).
**Why:** Concurrent updates fail loudly instead of silently corrupting schedules.

## ADR-007: Author-only commits
**Decision:** All commits are made by the repository owner from their own machine and Git identity.
**Why:** Accurate attribution; the author runs and reviews every milestone.

## ADR-008: Pin Testcontainers 1.21.4
**Decision:** Override Spring Boot's managed Testcontainers version to 1.21.4.
**Why:** Docker Desktop 29+ rejects the API version used by 1.21.3 (HTTP 400, "Could not find a valid Docker environment").
**Revisit:** Remove once the Spring Boot BOM ships 1.21.4 or later.

## ADR-009: Discrete 15-minute slots
**Decision:** The scheduler plans in fixed slots with constant power per slot.
**Why:** Turns continuous scheduling into array bookkeeping (fast, exact, easy to validate); matches how utilities bill demand.
**Trade-off:** A vehicle arriving mid-slot waits for the next slot boundary.

## ADR-010: Integer watts and watt-minutes inside the engine
**Decision:** Convert kW/kWh `BigDecimal` value objects to `long` watts / watt-minutes at the engine boundary.
**Why:** Exact (no floating-point drift on capacity checks) and much faster than `BigDecimal` in inner loops.

## ADR-011: Priority + least-slack ordering
**Decision:** Optimized scheduler orders by priority, then slack (time available − time needed), using a `PriorityQueue`.
**Why:** Least-slack-first is a classic deadline heuristic; serving tight vehicles first avoids the baseline's
failure where an early, flexible vehicle consumes capacity a tight one needed. It is a greedy heuristic, not
a proven optimum; an exact solver (MILP) is out of scope.

## ADR-012: Database exclusion constraints for reservations
**Decision:** `EXCLUDE USING gist (charger_id WITH =, tstzrange(...) WITH &&)` plus the same for vehicles.
**Why:** Application-level "check then insert" has a race window; the database constraint is atomic.

## ADR-013: OAuth2 resource server with locally issued HS256 JWTs
**Decision:** Spring Security resource server validating JWTs; a login endpoint issues them for local/demo use.
**Why:** No external identity provider to run or pay for, while the API code is identical to one that trusts Cognito/Keycloak.
**Trade-off:** Symmetric key must be protected; production should use an OIDC provider (asymmetric keys, MFA, rotation).

## ADR-014: In-memory token-bucket rate limiting
**Decision:** Per-instance `ConcurrentHashMap` of token buckets.
**Why:** Simple and testable for a single instance.
**Revisit:** With multiple ECS tasks (M8), move buckets to Redis so limits are global.

## ADR-015: Grid capacity enforced with a row lock at reservation time
**Decision:** `SELECT … FROM grid_connection … FOR UPDATE`, then sum overlapping live sessions, then insert.
**Why:** Capacity is a constraint across many rows; exclusion constraints cannot express "sum ≤ limit". Locking the
grid row serialises only reservations on the same grid.

## ADR-016: 404 for other tenants' resources
**Decision:** Cross-tenant ids are reported as not found.
**Why:** A 403 would confirm that the id exists, leaking information.
