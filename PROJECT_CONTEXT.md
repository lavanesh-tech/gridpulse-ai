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

## Milestone 4 — Optimization + Analytics: DONE (1a442ba, verified: 73 tests, 0 failures)
`c0e66ae` only re-recorded benchmark timings from a second run.
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

## Milestone 5 — Spring AI + RAG: DONE (d65c449, verified: 86 tests, 0 failures)
First run caught the lexical relevance threshold set too low (0.10); raised to 0.20 from measured collision noise.
- Spring AI 1.0.3 (OpenAI starter), all models `none` by default → app and tests need no key
- V5: `knowledge_document`, `knowledge_chunk` (vector(1536), HNSW cosine, embedding_model per chunk)
- Ingestion (chunk → embed → store, SHA-256 dedup), tenant-filtered search, relevance threshold, filtered-HNSW tuning
- `EnergyAdvisorService.ask` (refuse-before-generate, citation validation), `explainSchedule` (engine facts +
  algorithm comparison + optional narrative with read-only `AdvisorTools`, capped at 8 calls)
- Retrieval evaluation (hit@k, MRR) + bundled 14-question set; 4 synthetic sample documents
- Tests: `TextAndRetrievalUnitTest`, `AdvisorIT` (fake chat model: isolation, refusal, citations, type filter, dedup,
  tenant-scoped delete, evaluation, tool safety, explanation), ApiIT advisor flow without a key

## Milestone 6 — React Frontend: WRITTEN, awaiting verification on the Mac
- `frontend/`: React 19 + TypeScript 5.9 + Vite 7 + Tailwind 4 + TanStack Query 5 + ECharts 6 (tree-shaken), react-router 7
- Pages: sign-in, dashboard (analytics), fleets/vehicles, stations/chargers, planner (night-strip timeline, tariff bands,
  grid load vs capacity, strategy comparison, charger-failure what-if), advisor (cited answers)
- Role-aware UI (viewer cannot run plans); JWT kept in sessionStorage; 401 signs out
- Tests: 19 Vitest + Testing Library tests passed in the assistant workspace; tsc clean; production build OK
  (initial bundle 289 kB / 91 kB gzip; planner chunk lazy-loaded). package-lock.json is generated on first `npm install`.

## Known issues / limitations
- Deadlines-first helps urgent vehicles at the expense of priority 0 (by design); cost-first meets fewer deadlines than baseline.
- Recalculation re-plans from the next slot boundary (up to one slot lost); late failures may be unrecoverable under ALAP.
- Lexical embedder matches words, not meaning; OpenAI embeddings not yet evaluated (needs key).
- Rate limiting is per instance (in memory) → Redis in M8.
- Local ports: Postgres 5433, Redis 6380. App needs GRIDPULSE_JWT_SECRET (>= 32 bytes) to start.

## Next steps
1. Optional: add OPENAI_API_KEY, re-ingest samples, run /advisor/evaluate and record real-embedding results.
2. Verify + publish Milestone 6.
3. Milestone 7: testing + observability (E2E, k6, Actuator/Micrometer).
