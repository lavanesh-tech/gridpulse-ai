# GridPulse AI

Intelligent EV fleet charging coordination platform: generates feasible charging schedules across
multiple stations under grid-capacity, compatibility, deadline and tariff constraints, recalculates
after disruptions, and compares optimized schedules against a documented baseline.

> All data is **synthetic**. Benchmark numbers in this repo come only from reproducible runs
> described in [BENCHMARKS.md](BENCHMARKS.md); none represent real-world business impact.

**Status:** Milestone 3 (REST APIs + Security) — see [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md).

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
