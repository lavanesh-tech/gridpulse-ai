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

## Milestone 6 — React Frontend: DONE (5e7e24e, verified: 19 frontend tests on the Mac)
- `frontend/`: React 19 + TypeScript 5.9 + Vite 7 + Tailwind 4 + TanStack Query 5 + ECharts 6 (tree-shaken), react-router 7
- Pages: sign-in, dashboard (analytics), fleets/vehicles, stations/chargers, planner (night-strip timeline, tariff bands,
  grid load vs capacity, strategy comparison, charger-failure what-if), advisor (cited answers)
- Role-aware UI (viewer cannot run plans); JWT kept in sessionStorage; 401 signs out
- Tests: 19 Vitest + Testing Library tests passed in the assistant workspace; tsc clean; production build OK
  (initial bundle 289 kB / 91 kB gzip; planner chunk lazy-loaded). package-lock.json is generated on first `npm install`.

## Milestone 7 — Testing + Observability: VERIFIED on the Mac (5/5 E2E, load test passed)
- Actuator: public /actuator/health, /health/liveness, /health/readiness; /actuator/metrics and /prometheus ADMIN-only
- Custom metrics: gridpulse.schedule.compute (timer, per algorithm), gridpulse.schedule.vehicles (planned/unmet),
  gridpulse.advisor.questions (outcome), gridpulse.advisor.model.latency
- X-Request-Id on every request (echoed, validated, in every log line)
- JaCoCo gate in `check`: instructions >= 80%, branches >= 70% (measured 82% / 75% before M7)
- `ObservabilityIT`; Playwright E2E (`scripts/e2e.sh`, 5 tests); k6 load test (`scripts/load-test.sh`, runs in Docker)
- E2E and load tests use a throwaway Postgres + API (never .env, never OpenAI)

## Milestone 8 — Events + Integrations: DONE (a9af685, verified)
- `events` module: transactional outbox (V6 `outbox_event`), written in the same transaction as every session change
  (RESERVED/STARTED/COMPLETED/CANCELLED/INTERRUPTED); `OutboxRelay` (FOR UPDATE SKIP LOCKED, acks=all, idempotent
  producer) publishes to `gridpulse.session-events`, key = session id. Off unless GRIDPULSE_EVENTS_KAFKA_ENABLED=true.
- `notification-service/` (Gradle subproject, port 8081): idempotent Kafka consumer, own `notification` schema +
  Flyway, DLT for malformed messages, tenant-scoped GET /api/v1/notifications with the same JWTs.
- Kafka 3.9.1 (KRaft) in docker-compose on localhost:9094.
- Tests: `SessionEventsIT` (Testcontainers Kafka), `NotificationFlowIT`, `NotificationRulesTest`.
- `mcp-server/` (Gradle subproject): Spring AI MCP server over stdio; an API client of GridPulse (signs in as a user,
  re-signs in on 401), 5 read-only tools; `GridPulseToolsTest` with a mocked API.

## Milestone 9 — CI/CD: DONE (GitHub Actions run 41375619591: all 4 jobs green)
- `.github/workflows/ci.yml`: backend (Gradle build: all tests + coverage gate), frontend (npm ci/test/build),
  E2E (scripts/e2e.sh), Docker image builds. No deployment, no secrets needed.
- `Jenkinsfile`: same stages (written; not executed - no Jenkins server).
- Runners pinned to ubuntu-24.04: Playwright 1.56 cannot install browser deps on Ubuntu 26 (ubuntu-latest from Oct 2026).
- `scripts/stack.sh` verified on the Mac: all 6 containers up, web/API/notification smoke checks passed.
- `Dockerfile` (multi-stage, targets `api`, `notification`; non-root, JRE runtime), `frontend/Dockerfile` (nginx,
  /api reverse proxy, SPA fallback). docker-compose profile `app`; Kafka has internal + external listeners.
- `scripts/stack.sh` builds and runs the whole stack and smoke-tests it.

## Known issues / limitations
- Deadlines-first helps urgent vehicles at the expense of priority 0 (by design); cost-first meets fewer deadlines than baseline.
- Recalculation re-plans from the next slot boundary (up to one slot lost); late failures may be unrecoverable under ALAP.
- Lexical embedder matches words, not meaning. OpenAI embeddings measured: hit@3 1.0, MRR 0.952 (vs lexical 0.929 / 0.881).
- Rate limiting is per instance (in memory) → Redis in M8.
- Local ports: Postgres 5433, Redis 6380. App needs GRIDPULSE_JWT_SECRET (>= 32 bytes) to start.

## Next steps (all 9 milestones are complete)
1. Optional: AWS deployment (Bedrock, Terraform for ECS/ECR/RDS/S3, CloudWatch) - only when the author asks; costs money.
2. Optional: bump actions/upload-artifact to v5 when convenient (only a deprecation notice today).
3. Optional: split the planner chunk (ECharts) further; build warns it is > 500 kB (lazy-loaded already).
