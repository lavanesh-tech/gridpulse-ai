# Project Context (current verified state)

_Last updated: 2026-11-01_

## GitHub
https://github.com/lavanesh-tech/gridpulse-ai — branch `main`. Sole author: Lavanesh Thirukonda Mahendran.

## Milestone 1 — Foundation + Database: DONE (088fb76, verified)
## Milestone 2 — Fleet Management + Charging Engine: DONE (1688258, verified)
Benchmark results in BENCHMARKS.md.

## Milestone 3 — REST APIs + Security: DONE (82c0ab0, verified: 50 tests, 0 failures)
- Spring Security OAuth2 resource server; HS256 JWT issued by `POST /api/v1/auth/token`; BCrypt passwords (`app_user`, V3)
- Roles ADMIN / FLEET_MANAGER / OPERATOR / VIEWER via `@PreAuthorize`; tenant from JWT; tenant-scoped lookups (404 cross-tenant)
- REST controllers + DTOs + Bean Validation for fleets, vehicles, grid connections, stations/chargers, outages,
  sessions (reserve/cancel/start/complete/interrupt), schedule preview, audit events
- Reservation rules: compatibility, in-service, power limits, grid capacity (row lock + sweep), no overlap (DB)
- RFC 9457 error responses; token-bucket rate limiting; JDBC audit log on every write; Swagger UI
- Tests: `ApiIT` (auth, RBAC, validation, tenant isolation, full flow), extended `ChargingReservationIT`
  (grid capacity, cross-tenant), `RateLimiterTest`

## Milestone 4 — Optimization + Analytics: written, NOT YET VERIFIED on the author's machine
Engine changes compiled and checked in the assistant's workspace (JDK 21, javac, harness runs; all 31 scheduling
files byte-identical to the repo); Spring code and JUnit tests compile only in the full Gradle build.
- Tariffs: `TariffRate`/`GridTariff` (local-time bands, wrap midnight), `tariff` module API (GET/PUT), V4 migration (grid time zone)
- `ScheduleCost` (slot-split pricing in integer micro-units), `PriorityOutcome` per-priority metrics
- `PrioritySlackScheduler.Objective` DEADLINES_FIRST (default) / COST_FIRST; per-grid precomputed slot order
- `FixedBooking`: preview plans around live reservations; validator counts them
- `GridLoadProfile` (load per slot, peak, bottlenecks ≥95%)
- `ScheduleRecalculator` + `Disruption` (charger outages, grid curtailment) and `POST /schedules/recalculate`
- `AnalyticsService` summary + `GET /sessions/history` (paged)
- Benchmark: 3 algorithms, cost + delta, per-priority, 40-scenario aggregate
- Tests: TariffAndCostTest, ExistingBookingsTest, ScheduleRecalculatorTest, TariffValidationTest, SchedulerTest additions,
  ApiIT M4 flow (tariff RBAC/validation, preview around a reservation, recalculation, analytics, history)

## Known issues / limitations
- Deadlines-first helps urgent vehicles at the expense of priority 0 (by design); cost-first meets fewer deadlines than baseline.
- Recalculation re-plans from the next slot boundary (up to one slot lost); late failures may be unrecoverable under ALAP.
- Rate limiting is per instance (in memory) → Redis in M8.
- Local ports: Postgres 5433, Redis 6380. App needs GRIDPULSE_JWT_SECRET (>= 32 bytes) to start.

## Next steps
1. Author runs the publish script for M4.
2. Milestone 5: Spring AI Energy Advisor, pgvector RAG (ingestion, chunking, embeddings, tenant-filtered retrieval,
   citations, retrieval evaluation), controlled tool calling validated by the Java engine. Needs an OpenAI API key.
