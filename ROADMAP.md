# Roadmap

| # | Milestone | Key deliverables | Status |
|---|---|---|---|
| 1 | Foundation + Database | Gradle/Java 21, module skeleton, Flyway schema, Docker Compose (pgvector, Redis), shared value objects, modularity + migration tests | In progress — awaiting local verification |
| 2 | Fleet + Charging Engine | Entities/repos, compatibility rules, session model, baseline + priority-queue scheduler, constraint validator, seeded synthetic data generator, first benchmarks | Planned |
| 3 | REST APIs + Security | Controllers/DTOs/validation, OpenAPI, JPA + JDBC, Spring Security resource server, JWT/OIDC, RBAC, tenant isolation, rate limiting, audit log | Planned |
| 4 | Optimization + Analytics | Tariff-aware optimization, cost estimation, grid analysis, disruption recalculation, concurrency tests, baseline comparison reports | Planned |
| 5 | Spring AI + RAG | Ingestion, chunking, embeddings in pgvector, tenant-filtered retrieval, cited answers, validated tool calling, retrieval evaluation | Planned |
| 6 | React Frontend | Vite + TS + Tailwind, TanStack Query, ECharts schedule/Gantt + analytics, OIDC login | Planned |
| 7 | Testing + Observability | Coverage, Testcontainers suites, frontend + E2E tests, k6 load tests, Actuator/Micrometer metrics | Planned |
| 8 | DevOps + AWS | GitHub Actions CI, Docker images, Terraform (ECS, ECR, RDS, S3, CloudWatch), hardening, deployment docs | Planned — needs your AWS approval |

Each milestone ends with: local verification (`./scripts/verify.sh`) → commit and push by the author
→ PROJECT_CONTEXT.md updated.
