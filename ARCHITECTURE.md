# Architecture

## Style: Modular Monolith
One deployable Spring Boot application, split into business modules under `com.gridpulse.*`.
Boundaries are enforced by **Spring Modulith**: `ModularityTests` fails the build if a module uses
another module's internal classes or if a dependency cycle appears.

Why not microservices? My other project (DockMind AI) already demonstrates them. Here the goal is
strong boundaries without network/ops overhead — the scheduler needs consistent, transactional
access to vehicles, chargers and grid limits, which a single database makes simple and correct.

## Modules
| Module | Responsibility |
|---|---|
| `identity` | Tenants, users, roles, authentication (M3) |
| `fleet` | Fleets per tenant |
| `vehicle` | Vehicle specs, battery state |
| `station` | Stations, chargers, outages |
| `grid` | Grid connections and capacity limits |
| `session` | Reservations, charging session lifecycle |
| `scheduling` | Deterministic schedule generation + constraint validation |
| `tariff` | Time-of-use tariffs and cost estimation |
| `advisor` | Spring AI Energy Advisor (read + propose only) |
| `analytics` | Dashboards, reports, benchmarks |
| `shared` | Shared kernel value objects (`PowerKw`, `EnergyKwh`, `StateOfCharge`, `TimeWindow`, ...) |

## Layering inside each module
```
api/        controllers + DTOs (HTTP only, no business rules)
application/  use-case services, transactions
domain/     entities, value objects, rules — plain Java, no Spring/JPA in rules where possible
infra/      JPA repositories, JDBC queries, external clients
```
Modules talk through public services in their root package or through application events.

## Data
PostgreSQL 16 with pgvector. Schema owned by Flyway (`ddl-auto: validate`, Hibernate never alters
tables). Every business table has `tenant_id`; mutable tables have a `version` column for
optimistic locking. Redis (from M3/M4) for rate limiting and caching.

## Safety boundary for AI
```
LLM ──proposes──▶ advisor ──calls──▶ scheduling.validate(...) ──▶ accept / reject with reasons
```
The LLM never receives SQL access, secrets, or other tenants' documents.
