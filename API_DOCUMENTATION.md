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
| GET | `/api/v1/grid-connections/{id}` | any |
| GET | `/api/v1/grid-connections/{id}/tariff` | any |
| PUT | `/api/v1/grid-connections/{id}/tariff` | ADMIN |
| POST | `/api/v1/schedules/preview` | ADMIN, FLEET_MANAGER, OPERATOR |
| POST | `/api/v1/schedules/recalculate` | ADMIN, FLEET_MANAGER, OPERATOR |
| GET | `/api/v1/sessions/history?from=&to=&page=&size=` | any |
| GET | `/api/v1/analytics/summary?from=&to=` | any |
| GET | `/api/v1/audit-events?limit=` | ADMIN |
| GET | `/api/v1/advisor/status` | any |
| GET | `/api/v1/advisor/documents` | any |
| POST | `/api/v1/advisor/documents`, `/documents/samples` | ADMIN |
| DELETE | `/api/v1/advisor/documents/{id}` | ADMIN |
| POST | `/api/v1/advisor/ask` | any |
| POST | `/api/v1/advisor/explain-schedule` | ADMIN, FLEET_MANAGER, OPERATOR |
| POST | `/api/v1/advisor/evaluate` | ADMIN |

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
`algorithm`: `BASELINE`, `PRIORITY_SLACK` (deadlines first, recommended) or `PRIORITY_SLACK_COST` (cheapest slots first).

Returns `schedule` (sessions + unmet with reasons), `violations` (must be empty), `metrics` (deadlines overall and
`byPriority`, utilization, energy, `cost`), `gridProfiles` (load per slot, peak, utilization, bottleneck slots) and
`existingBookingsConsidered`. Live reservations (RESERVED/ACTIVE) in the horizon are planned around. Nothing is booked.

## Tariffs
`PUT /api/v1/grid-connections/{id}/tariff` replaces the grid's time-of-use bands (local time of the grid's `timeZone`):
```json
{ "bands": [ { "start": "21:00", "end": "06:00", "pricePerKwh": 0.11 },
             { "start": "06:00", "end": "16:00", "pricePerKwh": 0.18 },
             { "start": "16:00", "end": "21:00", "pricePerKwh": 0.32 } ] }
```
Bands are `[start, end)`, may wrap midnight, must not overlap, price 0–10 with at most 5 decimals. Uncovered time is
priced 0. Create a grid with `"timeZone": "America/New_York"` (IANA id; default UTC).

## Disruption what-if
`POST /api/v1/schedules/recalculate`
```json
{ "plan": { …same as preview… },
  "disruption": { "at": "2026-01-06T01:00:00Z", "chargersOutOfService": ["<charger id>"],
                  "gridCapacityKw": { "<grid id>": 120 } } }
```
Plans, applies the outage/curtailment from `at`, and re-plans the rest. Response: `before` (the original preview),
`result` (combined schedule, `sessionsKept`, `sessionsInterrupted`, `sessionsReplanned`, `newlyUnmetVehicles`,
`violations`), `metricsAfter`, `gridProfilesAfter`.

## Analytics and history
- `GET /api/v1/analytics/summary?from=…&to=…` — sessions by status, scheduled energy (kWh, clipped to the range,
  excluding cancelled), distinct vehicles, per-charger sessions/occupied hours/utilization. Range ≤ 366 days.
- `GET /api/v1/sessions/history?from=…&to=…&page=0&size=50` — sessions overlapping the range, newest first, paged (size ≤ 200).


## AI Energy Advisor
- `POST /api/v1/advisor/documents` `{"title","source","docType":"POLICY|TARIFF|GRID|PROCEDURE|OTHER","content"}` — plain
  text/Markdown up to 200,000 characters; identical content is stored once (`duplicate: true`).
- `POST /api/v1/advisor/documents/samples` — loads four clearly-labelled **synthetic** policy documents.
- `POST /api/v1/advisor/ask` `{"question","docType"?}` → `answer`, `grounded`, `citations` (`S1`… with document, chunk,
  score, excerpt), `sources`, `aiAvailable`, models used. Nothing relevant → fixed "not covered" answer, model not called.
- `POST /api/v1/advisor/explain-schedule` — same body as schedule preview → `facts` (engine output), `comparison` of all
  three algorithms, `narrative` (if a chat model is configured) and `toolCalls` (alternatives the model evaluated).
- `POST /api/v1/advisor/evaluate` `{"cases":[{"question","expectedDocumentTitle"}], "k":3}` → hit@k, MRR, per-question
  ranks. Empty body uses the bundled sample set.
