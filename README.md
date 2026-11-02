# GridPulse AI

[![CI](https://github.com/lavanesh-tech/gridpulse-ai/actions/workflows/ci.yml/badge.svg)](https://github.com/lavanesh-tech/gridpulse-ai/actions/workflows/ci.yml)

Intelligent EV fleet charging coordination platform: generates feasible charging schedules across
multiple stations under grid-capacity, compatibility, deadline and tariff constraints, recalculates
after disruptions, and compares optimized schedules against a documented baseline.

> All data is **synthetic**. Benchmark numbers in this repo come only from reproducible runs
> described in [BENCHMARKS.md](BENCHMARKS.md); none represent real-world business impact.

**Status:** all 9 milestones complete (CI/CD last) — see [ROADMAP.md](ROADMAP.md) and [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md).

## Results so far (synthetic data, see [BENCHMARKS.md](BENCHMARKS.md))
Over 40 seeded scenarios (50,000 vehicles), the deadlines-first scheduler meets 97.1% of deadlines vs 96.7% for a
first-come-first-served baseline, raises urgent-vehicle on-time rate from 96.4% to 99.6%, and lowers estimated energy
cost by ~30% under an invented time-of-use tariff — with zero hard-constraint violations. Lower-priority vehicles
pay for this (96.9% → 93.5%). Numbers are reproducible with `./gradlew benchmark`.

## Stack
Java 21 · Spring Boot 3.5 · Spring Modulith · PostgreSQL 16 + pgvector · Redis · Flyway · Gradle ·
Testcontainers. Later milestones add Spring Security/OAuth2, Spring AI, React + TypeScript, and AWS.

## Run locally
Prerequisites: JDK 21 and Docker Desktop. Local ports: Postgres 5433, Redis 6380.

```bash
cp .env.example .env          # then set GRIDPULSE_JWT_SECRET (openssl rand -base64 48) and GRIDPULSE_ADMIN_PASSWORD
./scripts/verify.sh           # builds, runs all tests, smoke-tests Docker Compose
docker compose up -d          # Postgres (pgvector) + Redis
set -a; source .env; set +a   # export the variables for the app
./gradlew bootRun             # app on :8080 -> /swagger-ui.html, /actuator/health
./gradlew benchmark          # baseline vs optimized scheduler -> benchmarks/results/
```

## Run the web console
```bash
./gradlew bootRun          # API on :8080 (needs GRIDPULSE_JWT_SECRET, see .env.example)
cd frontend && npm install && npm run dev   # http://localhost:5173, /api proxied to :8080
npm test                   # Vitest (runs in UTC)
```

## Run everything in Docker (one command)
```bash
./scripts/stack.sh        # builds images, starts Postgres, Redis, Kafka, API, notification service and web console
                          # web console: http://localhost:3000 (admin / password from .env)
./scripts/stack.sh down   # stop (data volume kept)
```

## Continuous integration
Every push runs [`.github/workflows/ci.yml`](.github/workflows/ci.yml): backend tests with Testcontainers + coverage
gate, frontend tests + build, Playwright end-to-end tests against a real stack, and the Docker image builds.
[`Jenkinsfile`](Jenkinsfile) defines the same pipeline for Jenkins. Nothing is deployed.

## Events and the notification service
```bash
./scripts/run-local.sh            # API on :8080; session changes are published to Kafka (localhost:9094)
./scripts/run-notifications.sh    # notification service on :8081, consuming those events
curl -H "Authorization: Bearer $TOKEN" localhost:8081/api/v1/notifications
```

## MCP server (use GridPulse from an AI assistant)
`mcp-server/` is a Model Context Protocol server (stdio). It signs in to the GridPulse API as a normal user, so tenant
isolation and roles still apply, and offers read-only tools: `listFleets`, `listVehicles`, `planTonight`,
`compareStrategies`, `askDepotDocuments`. Build it with `./gradlew :mcp-server:bootJar`, then add to Claude Desktop's
`claude_desktop_config.json`:
```json
{ "mcpServers": { "gridpulse": {
    "command": "java",
    "args": ["-jar", "/Users/<you>/Desktop/gridpulse-ai/mcp-server/build/libs/mcp-server-0.1.0-SNAPSHOT.jar"],
    "env": { "GRIDPULSE_API_URL": "http://localhost:8080", "GRIDPULSE_USERNAME": "<a viewer user>", "GRIDPULSE_PASSWORD": "<password>" }
} } }
```

## Tests, monitoring and load
```bash
./scripts/verify.sh      # backend tests (Testcontainers) + coverage gate + frontend tests/build
./scripts/e2e.sh         # Playwright browser tests against a throwaway API + database
./scripts/load-test.sh   # k6 load test (in Docker) against a throwaway stack -> benchmarks/results/load-test-latest.json
```
Health: `GET /actuator/health` (public; liveness/readiness sub-paths). Metrics: `GET /actuator/prometheus` (ADMIN token).
Every response carries `X-Request-Id`, which also appears in the server log line for that request.

## Enabling the AI Energy Advisor (optional)
Everything runs without an AI key: retrieval uses an offline embedder and the advisor returns sources and computed facts.
To use OpenAI (gpt-4o-mini + text-embedding-3-small):
```bash
./scripts/setup-openai.sh      # asks for the key (hidden), checks it, writes .env (git-ignored, chmod 600)
./scripts/evaluate-openai.sh   # embeds the sample docs with OpenAI, records hit@3 / MRR, asks a sample question
./scripts/run-local.sh         # run the API with .env settings
```
Switching embedding models re-embeds existing documents on the next ingest (vectors are model-specific).
Set a monthly spending limit in your OpenAI account. Tests never call OpenAI.

## Documentation
| File | Purpose |
|---|---|
| [PRODUCT_SPEC.md](PRODUCT_SPEC.md) | What the product does and its hard constraints |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Modular monolith design and module boundaries |
| [ROADMAP.md](ROADMAP.md) | 8 milestones |
| [DECISIONS.md](DECISIONS.md) | Architecture decision records |
| [BENCHMARKS.md](BENCHMARKS.md) | Benchmark methodology and measured results |
| [API_DOCUMENTATION.md](API_DOCUMENTATION.md) | REST API reference |
| [LEARNING_GUIDE.md](LEARNING_GUIDE.md) | Beginner-friendly walkthrough of every concept used |
| [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md) | Current, verified state of the project |
| [SESSION_HANDOFF.md](SESSION_HANDOFF.md) | How to resume work in a new session |

## Author
Lavanesh Thirukonda Mahendran — MS Computer Science, George Mason University (Dec 2026).
