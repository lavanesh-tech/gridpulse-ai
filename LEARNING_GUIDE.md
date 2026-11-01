# Learning Guide

A beginner-friendly explanation of everything in GridPulse AI. Sections are added as each milestone
introduces new concepts. Each section: **what it is → why we use it here → where to look in the code.**

---

## 1. The domain in plain English
An EV fleet is a group of electric vehicles owned by one company. Every night they must charge enough
to finish tomorrow's routes. Constraints:
- **kW vs kWh.** Power (kW) is a *rate*, like litres per minute. Energy (kWh) is an *amount*, like litres.
  A 50 kW charger running 30 minutes delivers 25 kWh. See `PowerKw.over(minutes)`.
- **State of charge (SoC)** is the battery's fill level in %. A 100 kWh battery going from 20% → 80%
  needs 60 kWh. See `StateOfCharge.energyNeededTo`.
- **Grid capacity.** A depot's connection to the utility can only supply so many kW in total. If ten
  150 kW chargers run at once on a 600 kW connection, that's 1,500 kW — not allowed.

## 2. Core Java used in Milestone 1
- **Records** (`record PowerKw(BigDecimal value)`): concise immutable classes. The *compact constructor*
  (`public PowerKw { ... }`) validates input, so an invalid object can never exist.
- **Value objects:** objects defined by their value, not identity. Two `PowerKw(50)` are equal. They make
  the code say what it means and stop you passing a kWh where kW is expected.
- **BigDecimal** instead of `double` for money and energy, because `double` cannot represent 0.1 exactly.
  We fix scale (3 decimals) so `50.0` and `50.000` compare equal.
- **Comparable** lets us sort power/energy values and use them in `PriorityQueue`s (Milestone 2).
- **Half-open intervals** `[start, end)`: `TimeWindow.overlaps` is `a.start < b.end && b.start < a.end`.
  This is the classic interval-overlap test you'll also see in interview problems (meeting rooms).

## 3. Spring Boot basics
- `@SpringBootApplication` turns on component scanning and auto-configuration: Spring sees Postgres and
  Flyway on the classpath and configures a `DataSource` and runs migrations automatically.
- `application.yml` uses `${VAR:default}` so secrets come from environment variables, never from code.
- **Actuator** exposes `/actuator/health` for load balancers and monitoring.

## 4. Modular Monolith
One application, many modules (`fleet`, `vehicle`, `scheduling`, ...). Each module's `package-info.java`
is annotated `@ApplicationModule`. `ModularityTests` uses Spring Modulith to scan the code and fail if,
for example, `tariff` reaches into `scheduling`'s internal classes. That gives you microservice-style
boundaries with none of the network calls, distributed transactions or deployment complexity.
Interview line: *"I chose a modular monolith because the scheduler needs strongly consistent access to
several aggregates; I enforce boundaries with automated architecture tests so it can be split later."*

## 5. PostgreSQL, SQL and Flyway
- `V1__foundation_schema.sql` is a **migration**. Flyway runs each `V<n>__*.sql` exactly once, in order,
  and records it in `flyway_schema_history`. Never edit an applied migration — add `V2__...` instead.
- `CHECK` constraints (e.g. `state_of_charge_pct BETWEEN 0 AND 100`) are the last line of defence: even a
  buggy service can't store invalid data.
- `tenant_id` on every table is the basis of multi-tenancy (Milestone 3 enforces it on every query).
- `version` columns enable **optimistic locking**: an update says "only if version is still 3"; if
  someone else changed it first, the update fails instead of overwriting their work.
- `ddl-auto: validate` makes Hibernate check entities against the schema but never change it.
- `CREATE EXTENSION vector` enables pgvector for the AI module later.

## 6. Docker and Docker Compose
`docker-compose.yml` starts Postgres (the `pgvector/pgvector` image) and Redis with one command. The
healthcheck lets `docker compose up --wait` block until Postgres accepts connections.

## 7. Testing in Milestone 1
- **Unit tests** (`ValueObjectTest`): fast, no Spring, test pure rules.
- **Architecture test** (`ModularityTests`): verifies module boundaries.
- **Integration test** (`FlywayMigrationIT`): **Testcontainers** starts a real Postgres in Docker,
  `@ServiceConnection` points Spring at it, the full app boots and Flyway migrates. This catches SQL
  errors an in-memory H2 database would hide.

## 8. Git workflow
You make every commit yourself. Small, meaningful commits per milestone:
`feat(foundation): ...`, `test: ...`, `docs: ...`. Never commit `.env`.

*Sections for Collections/DSA, scheduling algorithms, JPA vs JDBC, security, concurrency, Spring AI,
RAG, React, CI/CD, Terraform and AWS are added in the milestones that introduce them.*
