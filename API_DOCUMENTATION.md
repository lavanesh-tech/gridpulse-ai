# API Documentation

Live, always-current spec: run the app and open **`/swagger-ui.html`** (OpenAPI JSON at `/v3/api-docs`).
Click **Authorize** and paste the `accessToken` from the login call.

All endpoints except login, health and docs require `Authorization: Bearer <JWT>`.
The tenant is taken from the token, never from the request, so every call only ever sees the caller's own data.
Errors use RFC 9457 `application/problem+json`.

## Authentication
| Method | Path | Body | Result |
|---|---|---|---|
| POST | `/api/v1/auth/token` | `{"username","password"}` | `{"accessToken","tokenType":"Bearer","expiresIn"}` (HS256 JWT, 1 h) |

JWT claims: `sub` (username), `tenant_id`, `roles` (one of ADMIN, FLEET_MANAGER, OPERATOR, VIEWER), `iss`, `iat`, `exp`.

## Endpoints and required roles
| Method | Path | Roles |
|---|---|---|
| GET | `/api/v1/fleets`, `/api/v1/fleets/{id}` | any |
| POST | `/api/v1/fleets` | ADMIN, FLEET_MANAGER |
| GET | `/api/v1/fleets/{fleetId}/vehicles`, `/api/v1/vehicles/{id}` | any |
| POST | `/api/v1/fleets/{fleetId}/vehicles` | ADMIN, FLEET_MANAGER |
| PUT | `/api/v1/vehicles/{id}/state-of-charge` | ADMIN, FLEET_MANAGER, OPERATOR |
| GET | `/api/v1/grid-connections` | any |
| POST | `/api/v1/grid-connections` | ADMIN |
| GET | `/api/v1/stations`, `/api/v1/stations/{id}/chargers` | any |
| POST | `/api/v1/stations`, `/api/v1/stations/{id}/chargers` | ADMIN |
| POST | `/api/v1/stations/{id}/outage`, `/restore` | ADMIN, OPERATOR |
| POST | `/api/v1/sessions` (reserve) | ADMIN, FLEET_MANAGER, OPERATOR |
| GET | `/api/v1/sessions?chargerId=` | any |
| POST | `/api/v1/sessions/{id}/cancel` | ADMIN, FLEET_MANAGER, OPERATOR |
| POST | `/api/v1/sessions/{id}/start`, `/complete`, `/interrupt` | ADMIN, OPERATOR |
| POST | `/api/v1/schedules/preview` | ADMIN, FLEET_MANAGER, OPERATOR |
| GET | `/api/v1/audit-events?limit=` | ADMIN |

## Status codes
| Code | Meaning |
|---|---|
| 400 | Validation failed (`errors` map per field) or malformed input |
| 401 | Missing/invalid/expired token, or bad login |
| 403 | Authenticated but role not allowed |
| 404 | Not found **or belongs to another tenant** (deliberately indistinguishable) |
| 409 | Double booking, duplicate, illegal state transition, concurrent modification |
| 422 | Business rule broken (incompatible connector, out of service, power limit, grid capacity) |
| 429 | Rate limit exceeded (`Retry-After`) |

## Example
```bash
TOKEN=$(curl -s localhost:8080/api/v1/auth/token -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"<your GRIDPULSE_ADMIN_PASSWORD>"}' | jq -r .accessToken)
curl -s localhost:8080/api/v1/fleets -H "Authorization: Bearer $TOKEN"
```

## Schedule preview
`POST /api/v1/schedules/preview`
```json
{ "horizonStart": "2026-01-06T00:00:00Z", "slotMinutes": 15, "slotCount": 32, "algorithm": "PRIORITY_SLACK",
  "vehicles": [ { "vehicleId": "…", "targetSocPct": 80, "arrival": "2026-01-06T00:00:00Z",
                  "departure": "2026-01-06T06:00:00Z", "priority": 1 } ] }
```
Returns `schedule` (sessions + unmet with reasons), `violations` (must be empty) and `metrics`. It books nothing.
Limitation: the preview does not yet subtract capacity already held by existing reservations (Milestone 4).
