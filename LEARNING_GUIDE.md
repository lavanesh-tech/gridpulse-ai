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

---

# Milestone 2 concepts

## 9. Collections and DSA in the scheduler
| Structure | Where | Why |
|---|---|---|
| `PriorityQueue<ChargingRequest>` | `PrioritySlackScheduler` | Always take the most urgent vehicle next in O(log n). Ordering is a chained `Comparator`: priority ↓, slack ↑, departure ↑, id. |
| `HashMap<String, long[]>` | `CapacityLedger` | O(1) lookup of a grid's per-slot load array. |
| `long[]` / `String[]` per slot | `CapacityLedger` | Dense arrays: slot index → load or occupant. Fast and cache-friendly. |
| `HashSet` | `SchedulingProblem`, `ScheduleMetrics` | Detect duplicate ids; count unmet vehicles. |
| Sorting + scan | `ScheduleValidator.checkOverlaps` | Sort sessions by start; any start before the latest end seen so far is an overlap. O(n log n) — the "meeting rooms" interview problem. |
| Sweep line | `ScheduleValidator.checkGridCapacity` | Turn each session into +power at start, −power at end; sort; running sum = load at each instant. |

## 10. Scheduling algorithms
- **Greedy:** make the best local choice for one vehicle, commit, move on. Fast and explainable; not guaranteed optimal.
- **Baseline (FCFS):** whoever arrives first takes full power. Simple, but an early vehicle with a late departure
  can block a later vehicle with a tight deadline. `SchedulerTest.baselineMisses...` shows this exact case.
- **Slack** = time plugged in − time needed at full rate. Zero slack means "must charge continuously at full power".
  Least-slack-first is related to Earliest-Deadline-First (EDF) from real-time operating systems.
- **Power modulation:** the optimized scheduler can charge at 40 kW when only 40 kW of grid headroom remains,
  instead of waiting for 100 kW to free up.
- **Determinism:** ties are broken by id so the same input always gives the same output — essential for testing,
  benchmarking and auditing.
- **Complexity:** O(V log V + V·C·S). For 2,000 vehicles × ~150 compatible chargers × 64 slots that is ~19M cheap steps.

## 11. Why integers inside the engine
`0.1 + 0.2 != 0.3` in `double`. Capacity checks must be exact, so the engine converts kW to `long` watts and kWh to
watt-minutes (1 kWh = 60,000 Wmin). `BigDecimal` stays at the API/database boundary where readability matters.

## 12. JPA, Hibernate and entities
- **JPA** is the Java standard API for mapping objects to tables; **Hibernate** is the implementation;
  **Spring Data JPA** generates repository implementations from interfaces (`findByTenantId` becomes SQL).
- Entities have a `protected` no-arg constructor (Hibernate needs it) and a public constructor that validates
  input, so invalid entities cannot be created in code.
- Modules reference each other **by id** (`UUID fleetId`), not by `@ManyToOne` objects. That keeps module
  boundaries clean and avoids accidental lazy-loading across modules.
- `@Version Long version` = optimistic locking. `null` also tells Spring Data the entity is new (INSERT, not merge).

## 13. Concurrency: why the database enforces no double-booking
Naive code: `if (!isBooked(charger, window)) save(...)`. Two requests can both pass the check before either
saves (a **race condition**). Fix: PostgreSQL **exclusion constraint**
`EXCLUDE USING gist (charger_id WITH =, tstzrange(starts_at, ends_at, '[)') WITH &&)`, meaning "no two rows
with the same charger may have overlapping time ranges". The database checks atomically, so one insert wins and
the other fails with SQLState 23P01, which Spring translates to `DataIntegrityViolationException`, which we turn
into `ReservationConflictException`. `ChargingReservationIT` fires two threads at once to prove it.

## 14. State machines
`ChargingSession` only allows legal transitions (RESERVED→ACTIVE→COMPLETED, ACTIVE→INTERRUPTED, RESERVED→CANCELLED).
Illegal ones throw `IllegalStateException`. Only live statuses (RESERVED, ACTIVE) block the charger, so a cancelled
reservation frees the slot.

## 15. Benchmarking honestly
Seeded data (reproducible), warm-up runs (JIT compilation), several measured runs (median and p95), the same input for
both algorithms, every schedule validated, environment recorded. Never quote a number that isn't in the results file.

---

# Milestone 3 concepts

## 16. REST API design
- **Resources are nouns** (`/fleets`, `/vehicles/{id}`); HTTP verbs are actions (GET read, POST create/command, PUT replace).
- **Commands on state machines** are sub-resources: `POST /sessions/{id}/cancel`.
- **Status codes carry meaning**: 201 created, 400 bad input, 401 who are you, 403 not allowed, 404 not found,
  409 conflicts with current state, 422 valid shape but breaks a rule, 429 slow down.
- **DTOs** (`CreateFleetRequest`, `FleetResponse`) are separate from entities, so the database shape can change
  without breaking clients and clients can never set fields like `tenantId` or `version`.
- **Bean Validation** (`@NotBlank`, `@Positive`, `@Pattern`) rejects bad input before any business code runs.
- **Problem Details (RFC 9457)**: one consistent error format, produced in one place (`ApiExceptionHandler`).

## 17. Authentication vs authorization
- **Authentication**: proving who you are (username + password → JWT).
- **Authorization**: what you may do (roles via `@PreAuthorize`, tenant scoping in services).

## 18. JWT
A JWT is three base64 parts: header, claims, signature. The server signs `{sub, tenant_id, roles, exp}` with a secret
key; anyone can *read* the claims but nobody can *change* them without breaking the signature. The API is
**stateless**: no server-side session, the token carries identity, so any instance can serve any request.
Never put secrets in claims. Short expiry (1 h) limits damage from a stolen token.

## 19. OAuth2 and OIDC in one paragraph
OAuth2 defines roles: the **authorization server** issues tokens, the **resource server** (our API) validates them,
the **client** (React app) carries them. OIDC adds a standard identity layer on top (who the user is). Our API is a
resource server; for local use it also issues tokens itself. In production you'd point it at Cognito or Keycloak.

## 20. Password storage
Never store passwords. Store a **BCrypt hash**: slow on purpose and salted, so stolen hashes are expensive to crack.
On login we compare against a dummy hash when the user doesn't exist, so response time doesn't reveal valid usernames.

## 21. Multi-tenancy
Many customers share one database; every row has `tenant_id`. The rule: **the tenant comes from the token, never the
request**. Every lookup is `findByIdAndTenantId`. If tenant B asks for tenant A's fleet id, the answer is 404 —
the same as for a non-existent id — so nothing leaks.

## 22. Rate limiting: token bucket
Each user has a bucket of N tokens; each request spends one; tokens refill at N per minute. Allows short bursts,
caps sustained rate. Login is limited much harder to slow password guessing.

## 23. Transactions and locks (grid capacity)
"Grid load ≤ limit" depends on *many* rows, so a unique/exclusion constraint can't enforce it. We open a transaction,
`SELECT … FOR UPDATE` the grid row (other reservations on the same grid now wait), sum overlapping sessions with a
sweep line, insert if it fits, commit. Two concurrent requests are forced to happen one after the other.
**Pessimistic locking** (lock first) suits short, contended operations; **optimistic locking** (`@Version`, fail on
conflict) suits rare conflicts — the project uses both, each where it fits.

## 24. JDBC vs JPA in this codebase
| Use | Tool | Why |
|---|---|---|
| Entities with behaviour and lifecycle | JPA/Hibernate | Dirty checking, optimistic locking, less boilerplate |
| Insert-only audit rows | `JdbcTemplate` | One SQL statement; no entity needed |
| Schedule preview read model (join of chargers, stations, grids) | `JdbcTemplate` | One query, exactly the columns needed |
| `FOR UPDATE` lock + aggregate | `JdbcTemplate` | Explicit SQL is clearer than JPQL lock hints |

---

# Milestone 4 concepts

## 25. Optimization means choosing an objective
"Optimize" is meaningless until you say *for what*. Here two objectives conflict: meeting departures and paying less.
Cheap hours are the same for everyone, so if every vehicle chases them they collide and some miss departure.
The project makes the objective an explicit parameter and measures both, instead of picking one and claiming victory.

## 26. Greedy heuristics and why "latest first" helped
The scheduler is greedy: it plans one vehicle at a time and never revisits. Order matters. Most vehicles arrive in
the first hours, so early slots are the scarce ones. If flexible vehicles grab them, tight vehicles arriving a bit
later find nothing. Scheduling *as late as possible* (ALAP) leaves early capacity to whoever needs it. ALAP's risk:
no buffer if something fails late. Every heuristic has a failure mode — know it and test it.

## 27. Measure, don't guess
The M2 optimizer *looked* smart and lost to the baseline. M4 changed one thing at a time (charger choice, slot order,
cost order, fallback), ran each across 40 seeded scenarios, and kept only what moved the numbers. Two ideas did
nothing and were dropped. Write down what didn't work (BENCHMARKS.md "History") — interviewers love that.

## 28. Time zones and tariffs
Store instants in UTC (`Instant`, `TIMESTAMPTZ`); convert to local time only to apply local rules like tariffs.
A band 21:00–06:00 *wraps midnight*: `covers(t) = t ≥ start OR t < end`. `ZonedDateTime` handles daylight saving.

## 29. Money arithmetic
Prices are converted to integer micro-units (0.11 → 110,000) and multiplied exactly; rounding happens once, at the
end. Never use `double` for money.

## 30. Profiling a slowdown
Cost ordering made the optimizer 8× slower than baseline. Cause: building and sorting a boxed `List<Integer>` for every
vehicle × charger pair (millions of allocations). Fix: the cheapest-first order is the same for every vehicle on a grid,
so compute it once and *filter* per vehicle. Same output, 2.3× faster. General lesson: hoist work that doesn't depend
on the loop variable out of the loop.

## 31. Re-planning after a disruption
Real operations change mid-night: a station fails, the utility curtails power. The recalculator freezes the past,
credits energy already delivered, and re-runs the same deterministic scheduler on what's left — then validates the
combined result. Re-using one well-tested engine is safer than writing a separate "repair" algorithm.

## 32. Analytics in SQL
Aggregates (count by status, energy = power × duration clipped to a date range, utilization per charger) are computed
in PostgreSQL with `GROUP BY`, `LEAST/GREATEST` and `EXTRACT(EPOCH …)`. Pushing aggregation to the database moves a
few rows over the network instead of every session.

---

# Milestone 5 concepts

## 33. What an LLM is good and bad at here
Good: turning numbers and documents into a clear explanation. Bad: arithmetic, constraint checking, knowing your
company's policies. So the design gives each job to the right component: Java computes, SQL retrieves, the LLM explains.

## 34. Embeddings
An embedding model turns text into a list of numbers (here 1536) so that texts with similar meaning are close
together. "Closeness" is measured with **cosine similarity** (angle between vectors): 1 = same direction, 0 = unrelated.
pgvector's `<=>` operator returns cosine *distance* = 1 − similarity.

## 35. RAG — Retrieval-Augmented Generation
1. **Ingest:** split documents into chunks (~800 characters, 150 overlap, heading kept) → embed → store.
2. **Retrieve:** embed the question → find the nearest chunks for *this tenant*.
3. **Generate:** give the model only those chunks, numbered [S1]…[Sn], and require citations.
Why chunk? Models have limited context and a whole document dilutes the match. Why overlap? So a fact cut in half by
a boundary still appears whole in one chunk.

## 36. pgvector and HNSW
`vector(1536)` is a column type. Exact search compares the query with every row (slow at scale). **HNSW** is a
graph index that finds *approximately* nearest neighbours fast. Catch: filters (`WHERE tenant_id = ?`) apply after the
index proposes candidates, so you may get fewer results than asked — see ADR-024 for the fix used.

## 37. Grounding and citations
"Grounded" means every claim comes from a provided source. We enforce what we can in code: no relevant source → no
model call; citations are parsed and checked against the sources actually sent; invalid ones are dropped.

## 38. Tool calling (function calling)
The model can ask the application to run a function (`@Tool` methods) and gets the result back before answering.
Here tools only *re-run the deterministic scheduler* for alternatives (another algorithm, a priority change). The model
can suggest; Java decides feasibility. Nothing is saved.

## 39. Prompt injection
A document could contain "ignore your instructions and…". Defences used: sources are labelled untrusted in the system
prompt; the model has no write tools; tenant scope is fixed in code, not chosen by the model.

## 40. Evaluating retrieval
**hit@k**: did the right document appear in the top k? **MRR**: average of 1/rank of the first right result.
The sample set mixes keyword questions with paraphrases; the offline embedder scores 0.93 hit@3, failing a paraphrase
("cheapest overnight" vs "off-peak") — exactly the gap real semantic embeddings should close. Measure, then claim.

## 41. React components and state
UI is a function of state. Components receive props, keep local state with `useState`, and re-render when it changes.

## 42. TypeScript at the API boundary
`api/types.ts` mirrors the backend DTOs, so a renamed field fails the build instead of failing in the browser.

## 43. Server state with TanStack Query
Data from the server is cached by a query key; mutations invalidate keys so lists refresh. Loading and error states come for free.

## 44. Vite and code splitting
`import()` of a page creates a separate chunk that downloads only when the route is visited.

## 45. Tailwind
Utility classes composed in markup; design tokens (colors, fonts) live once in `styles.css` under `@theme`.

## 46. Testing UIs
Testing Library queries by role and label, the way a user finds things; `fetch` is mocked so tests need no backend.
Time-dependent tests run with `TZ=UTC` so they pass on any machine.

## 47. Health checks: liveness vs readiness
Liveness = "the process is not stuck" (restart if it fails). Readiness = "send me traffic" (e.g. database reachable).

## 48. Metrics with Micrometer
Counters only go up (vehicles planned), timers record durations (scheduler run), tags split them (algorithm). Prometheus
scrapes `/actuator/prometheus`; percentiles like p95 say "95% of requests were faster than this".

## 49. Correlation ids
One id per request, written on every log line and returned to the caller, lets you find all logs for one failing call.

## 50. Code coverage as a gate
Coverage shows which code tests executed, not whether they assert the right thing. A floor stops untested code creeping in.

## 51. End-to-end tests
Playwright drives a real browser through the real stack. Few, slow, valuable: they catch wiring bugs unit tests cannot.

## 52. Load testing
k6 "constant arrival rate" sends a fixed number of requests per second regardless of response time (an open model),
which shows how latency grows under load instead of hiding it.

## 53. Kafka basics
A topic is split into partitions; records with the same key go to the same partition, in order. Consumers in one
group share partitions; offsets record how far each group has read.

## 54. The dual-write problem and the transactional outbox
Never "save to DB, then send to Kafka" as two steps. Save the event in the same DB transaction, relay it afterwards.

## 55. Delivery guarantees and idempotent consumers
At-least-once means duplicates can arrive. Store each processed event id under a unique constraint and skip repeats.

## 56. Dead-letter topics
A message that can never succeed is moved aside so it does not block every message behind it.

## 57. Monolith vs microservices
Extract a service when it has its own reason to change, scale or fail; otherwise modules in one deployable are simpler.

## 58. RAG vs tool calling vs MCP
RAG puts relevant documents into the prompt. Tool calling lets a model ask your code to run a function. MCP is a
standard protocol so any AI app can discover and call your tools; here it wraps the same safe, read-only operations.
