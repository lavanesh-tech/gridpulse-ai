# Project Context (current verified state)

_Last updated: 2026-11-01_

## GitHub
https://github.com/lavanesh-tech/gridpulse-ai — branch `main`. Sole author: Lavanesh Thirukonda Mahendran.

## Milestone 1 — Foundation + Database: DONE (088fb76, verified)
## Milestone 2 — Fleet Management + Charging Engine: DONE (1688258, verified)
Benchmark results in BENCHMARKS.md.

## Milestone 3 — REST APIs + Security: written, NOT YET VERIFIED
- Spring Security OAuth2 resource server; HS256 JWT issued by `POST /api/v1/auth/token`; BCrypt passwords (`app_user`, V3)
- Roles ADMIN / FLEET_MANAGER / OPERATOR / VIEWER via `@PreAuthorize`; tenant from JWT; tenant-scoped lookups (404 cross-tenant)
- REST controllers + DTOs + Bean Validation for fleets, vehicles, grid connections, stations/chargers, outages,
  sessions (reserve/cancel/start/complete/interrupt), schedule preview, audit events
- Reservation rules: compatibility, in-service, power limits, grid capacity (row lock + sweep), no overlap (DB)
- RFC 9457 error responses; token-bucket rate limiting; JDBC audit log on every write; Swagger UI
- Tests: `ApiIT` (auth, RBAC, validation, tenant isolation, full flow), extended `ChargingReservationIT`
  (grid capacity, cross-tenant), `RateLimiterTest`

## Known issues / limitations
- Optimized scheduler does not yet outperform baseline on overall deadline success (M2 benchmark) → M4.
- Schedule preview ignores capacity already held by existing reservations → M4.
- Rate limiting is per instance (in memory) → Redis in M8.
- Local ports: Postgres 5433, Redis 6380. App needs GRIDPULSE_JWT_SECRET (>= 32 bytes) to start.

## Next steps
1. Author runs the publish script for M3.
2. Milestone 4: tariff/cost model, cost-aware optimization, per-priority metrics, optimizer improvement,
   disruption recalculation, preview that respects existing reservations.
