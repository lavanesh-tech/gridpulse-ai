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

## Scheduling engine (Milestone 2)
`scheduling` is plain Java with no Spring or JPA, so it is fast to test and benchmark.
```
SchedulingProblem ──▶ ChargingScheduler (Baseline | PrioritySlack) ──▶ ChargingSchedule
                                                                          │
                                         ScheduleValidator (independent) ◀┘ ──▶ List<Violation>
```
- Time is split into 15-minute slots. A `CapacityLedger` tracks watts used per grid per slot and which
  vehicle occupies each charger per slot, and refuses any allocation that breaks a hard constraint.
- `ScheduleValidator` re-checks every hard constraint without trusting the scheduler (sweep line for grid
  load, sort-and-scan for overlaps). The AI advisor's proposals will go through the same validator.

## Double-booking defence in depth
1. Scheduler ledger (in memory).
2. Validator (any schedule, any source).
3. PostgreSQL exclusion constraints on `charging_session` (charger and vehicle, live statuses only), which
   hold even when two requests race on different app instances.

## Security (Milestone 3)
```
request ─▶ BearerTokenAuthenticationFilter (validate JWT signature, issuer, expiry)
        ─▶ RateLimitFilter (per user / per IP, stricter for login)
        ─▶ URL rules (public: login, health, docs; everything else authenticated)
        ─▶ @PreAuthorize role check on the controller method
        ─▶ service: tenant id from CurrentUser (JWT claim) → findByIdAndTenantId(...)
```
- The API is an OAuth2 **resource server**. Tokens are issued locally (HS256) for development/demo; swapping
  the `JwtDecoder` for an issuer URI accepts tokens from an external OIDC provider instead.
- **Tenant isolation:** every repository lookup includes `tenantId`; other tenants' ids return 404, not 403.
- **Audit:** every write appends to `audit_event` (JSONB details) via Spring JDBC.
- Module dependencies are one-directional and checked by `ModularityTests`:
  `identity → audit`; `fleet, grid → identity, audit`; `vehicle → fleet`; `station → grid`;
  `session → vehicle, station`; `scheduling → identity`.

## AI Energy Advisor (Milestone 5)
```
question ─▶ embed (OpenAI or offline lexical) ─▶ pgvector search  WHERE tenant_id = <from JWT>  ─▶ top-k chunks
                                                                                                 │ score ≥ threshold?
                                                       no ──▶ "documents do not cover this" (model NOT called)
                                                       yes ─▶ LLM with numbered sources ─▶ answer ─▶ keep only
                                                                                          citations [Sn] that exist
plan ─▶ deterministic scheduler + validator ─▶ facts JSON ─▶ LLM narrative
                                                   ▲            │ may call read-only tools
                                                   └── AdvisorTools (re-run engine; feasible only if 0 violations)
```
**Safety boundary.** The model never sees SQL, secrets, other tenants' data or write APIs. Tenant comes from the JWT
and is bound into every query and tool instance. Tools are read-only re-evaluations and capped at 8 per request.
Retrieved text is treated as untrusted (the system prompt tells the model to ignore instructions inside sources).
Every advisor call is audited (question hash, source/citation counts, whether the model was called).
**Degrades without a key:** retrieval, facts and comparisons still work; only the natural-language answer is absent.

## Web console (Milestone 6)
- `frontend/` is a separate Vite app; in dev, `/api` is proxied to the Spring Boot app, so there is no CORS setup.
- `api/client.ts` is the only place that calls `fetch`: adds the bearer token, maps RFC 9457 problems to `ApiError`, signs out on 401.
- `api/hooks.ts` wraps every endpoint in TanStack Query; mutations invalidate the queries they change.
- `lib/schedule.ts` turns API schedules into chart data (lanes, bars, tariff bands, load steps) as pure, unit-tested functions.
- The planner page and ECharts load lazily so sign-in and dashboard stay small.
- The UI enforces roles only for convenience; the server is the authority (RBAC + tenant from JWT).

## Observability (Milestone 7)
- Spring Boot Actuator + Micrometer with the Prometheus registry. Probes are public so a load balancer or ECS can poll
  them; metrics and health details need an ADMIN token because they reveal internals.
- Business metrics sit next to the code they measure: the scheduler timer wraps only the engine call (not data loading),
  and advisor outcomes distinguish refusals, sources-only answers (no model), grounded and ungrounded answers.
- `RequestIdFilter` runs first: one id per request in MDC, so logs from every module can be correlated.
- Test layers: unit (engine, chunker, client), integration (Testcontainers Postgres, real security chain),
  browser E2E (Playwright, real stack), load (k6). E2E and load use throwaway infrastructure started by scripts.

## Events and the notification service (Milestone 8)
```
ChargingSessionService --(same DB transaction)--> outbox_event --OutboxRelay--> Kafka gridpulse.session-events
                                                                                      |
                                                       notification-service (consumer group) --> notification schema
```
- Why an outbox: writing to the database and to Kafka are two systems; doing both directly can lose an event (crash
  after commit) or publish one for a rolled-back change. The outbox makes the event part of the database commit.
- Delivery is at-least-once; the consumer is idempotent (processed_event primary key), so duplicates are harmless.
- Ordering: the key is the session id, so all events of one session land in one partition, in order.
- Poison messages go to `<topic>-dlt` after retries instead of blocking the partition.
- The notification service is the one extracted microservice: separately deployable, owns its schema, talks to
  GridPulse only through Kafka events and the shared JWT format. The core stays a modular monolith (ADR-001).

## MCP server
```
AI assistant (Claude Desktop) --stdio JSON-RPC--> mcp-server --HTTPS + JWT--> GridPulse REST API
```
The MCP server holds no data and no privileges of its own: it is an API client signed in as a GridPulse user, so tenant
isolation, roles and rate limits are enforced by the same code as for the web console. Tools are read-only (planning
uses the preview endpoint) and return compact summaries instead of raw schedules.
