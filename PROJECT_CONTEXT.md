# Project Context (current verified state)

_Last updated: 2026-11-01_

## GitHub
https://github.com/lavanesh-tech/gridpulse-ai — branch `main`. Sole author: Lavanesh Thirukonda Mahendran.

## Milestone 1 — Foundation + Database: DONE (verified)
Commit `088fb76`. Verified on author's Mac (JDK 21.0.12, Docker Desktop): `./gradlew build` passed with
9/9 tests; Compose smoke test passed (Postgres OK, Redis PONG).

## Milestone 2 — Fleet Management + Charging Engine: written, NOT YET VERIFIED
- `scheduling`: `ChargingRequest`, `ChargerSpec`, `GridLimit`, `SchedulingProblem` (15-min slots),
  `CapacityLedger`, `BaselineScheduler` (FCFS), `PrioritySlackScheduler` (PriorityQueue, least slack, power
  modulation), `ScheduleValidator` (10 violation types, sweep line), `ScheduleMetrics`, `SyntheticScenarioGenerator`
- `analytics`: `SchedulingBenchmark` (`./gradlew benchmark` → `benchmarks/results/`)
- JPA entities + repositories: `Fleet`, `Vehicle`, `GridConnection`, `ChargingStation`, `Charger`, `ChargingSession`
- `ChargingSessionService.reserve/cancel`; session state machine
- Flyway `V2__charging_sessions.sql` with btree_gist exclusion constraints (charger + vehicle overlap)
- Tests: scheduler behaviour, 10 seeded property-style runs with 0 violations, validator per violation type,
  session lifecycle, concurrent reservation IT (Testcontainers), migration IT

## Known issues / limitations
- Tenant ownership checks for reserve() arrive with Milestone 3 security.
- Cost/tariff optimization, disruption recalculation: Milestone 4.
- Local ports are 5433 (Postgres) and 6380 (Redis) to avoid clashing with other local databases.

## Next steps
1. Author runs `./scripts/publish-milestone.sh "<M2 message>"` (verify → benchmark → commit → push).
2. Record benchmark results in BENCHMARKS.md from the generated JSON.
3. Milestone 3: REST APIs, DTOs, validation, OpenAPI, Spring Security, JWT/OIDC, RBAC, tenant isolation.
