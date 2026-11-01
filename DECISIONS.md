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
