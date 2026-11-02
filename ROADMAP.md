# Roadmap

| # | Milestone | Key deliverables | Status |
|---|---|---|---|
| 1 | Foundation + Database | Gradle/Java 21, module skeleton, Flyway schema, Docker Compose (pgvector, Redis), shared value objects, modularity + migration tests | Done — verified, pushed (088fb76) |
| 2 | Fleet + Charging Engine | Entities/repos, compatibility rules, session model, baseline + priority-queue scheduler, constraint validator, seeded synthetic data generator, first benchmarks | Done — verified, pushed (1688258) |
| 3 | REST APIs + Security | Controllers/DTOs/validation, OpenAPI, JPA + JDBC, Spring Security resource server, JWT/OIDC, RBAC, tenant isolation, rate limiting, audit log | Done — verified, pushed (82c0ab0) |
| 4 | Optimization + Analytics | Tariffs + cost model, deadlines-first vs cost-first objectives, per-priority metrics, existing-reservation-aware planning, grid load profiles, disruption recalculation, analytics summary, session history, 40-scenario benchmark | Done — verified, pushed (1a442ba) |
| 5 | Spring AI + RAG | Ingestion, chunking, embeddings in pgvector, tenant-filtered retrieval, cited answers, validated tool calling, retrieval evaluation | Done — verified, pushed (d65c449) |
| 6 | React Frontend | Vite + TS + Tailwind, TanStack Query, ECharts night-strip schedule timeline + analytics, planner + what-if, advisor, JWT sign-in | Done — verified, pushed (5e7e24e) |
| 7 | Testing + Observability | Health probes, Prometheus metrics (scheduler, advisor, HTTP), request ids, coverage gate, Playwright E2E, k6 load test | Done — verified, pushed (052a6af) |
| 8 | Events + Integrations | Transactional outbox → Kafka (KRaft), notification microservice (idempotent consumer, dead-letter topic, schema per service), MCP server for the advisor tools | Done — verified, pushed (a9af685) |
| 9 | CI/CD | GitHub Actions + Jenkinsfile, multi-stage Docker images, whole stack via Docker Compose | Done — verified; GitHub Actions green (run 41375619591) |
| — | AWS (optional, later) | Bedrock, Terraform (ECS, ECR, RDS, S3, Lambda, API Gateway), CloudWatch | Not planned until the author asks |

Each milestone ends with: local verification (`./scripts/verify.sh`) → commit and push by the author
→ PROJECT_CONTEXT.md updated.
