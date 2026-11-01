# Roadmap

| # | Milestone | Key deliverables | Status |
|---|---|---|---|
| 1 | Foundation + Database | Gradle/Java 21, module skeleton, Flyway schema, Docker Compose (pgvector, Redis), shared value objects, modularity + migration tests | Done — verified, pushed (088fb76) |
| 2 | Fleet + Charging Engine | Entities/repos, compatibility rules, session model, baseline + priority-queue scheduler, constraint validator, seeded synthetic data generator, first benchmarks | Done — verified, pushed (1688258) |
| 3 | REST APIs + Security | Controllers/DTOs/validation, OpenAPI, JPA + JDBC, Spring Security resource server, JWT/OIDC, RBAC, tenant isolation, rate limiting, audit log | Done — verified, pushed (82c0ab0) |
| 4 | Optimization + Analytics | Tariffs + cost model, deadlines-first vs cost-first objectives, per-priority metrics, existing-reservation-aware planning, grid load profiles, disruption recalculation, analytics summary, session history, 40-scenario benchmark | In progress — awaiting local verification |
| 5 | Spring AI + RAG | Ingestion, chunking, embeddings in pgvector, tenant-filtered retrieval, cited answers, validated tool calling, retrieval evaluation | Planned |
| 6 | React Frontend | Vite + TS + Tailwind, TanStack Query, ECharts schedule/Gantt + analytics, OIDC login | Planned |
| 7 | Testing + Observability | Coverage, Testcontainers suites, frontend + E2E tests, k6 load tests, Actuator/Micrometer metrics | Planned |
| 8 | DevOps + AWS | GitHub Actions CI, Docker images, Terraform (ECS, ECR, RDS, S3, CloudWatch), hardening, deployment docs | Planned — needs your AWS approval |

Each milestone ends with: local verification (`./scripts/verify.sh`) → commit and push by the author
→ PROJECT_CONTEXT.md updated.
