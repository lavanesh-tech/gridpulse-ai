# Product Specification

## Problem
A fleet operator (delivery vans, buses) must charge many EVs overnight or between shifts across
several depots. Each depot has a grid connection with a hard power limit, chargers with specific
connectors and power ratings, and time-of-use electricity prices. Vehicles have different battery
sizes, current charge levels, departure times and priorities. Manually planning this is error-prone
and expensive; overloading the grid connection can trip breakers or incur penalties.

## Users and roles
| Role | Can |
|---|---|
| ADMIN | Manage tenant users, all configuration |
| FLEET_MANAGER | Manage fleets/vehicles, generate and approve schedules |
| OPERATOR | Record sessions, report outages/interruptions, trigger recalculation |
| VIEWER | Read-only dashboards and schedules |

Every user belongs to exactly one tenant; no data crosses tenants.

## Hard constraints (must never be violated by any schedule)
1. Sum of power drawn at a grid connection ≤ its capacity limit at every instant.
2. A charger only serves vehicles with a matching connector type.
3. A vehicle occupies at most one charger at any instant.
4. A charger serves at most one vehicle at any instant.
5. Charging power ≤ min(charger max power, vehicle max charge rate).
6. Offline stations / out-of-service chargers receive no sessions.
7. Concurrent reservation attempts on the same charger/time cannot both succeed.

## Soft objectives (optimized, in priority order)
1. Meet each vehicle's target state of charge before departure (higher priority first).
2. Minimize estimated electricity cost under the tariff.
3. Maximize charger utilization / reduce peak load.

A vehicle whose target is infeasible is reported explicitly with the reason — never silently dropped.

## Baseline
"First-come, first-served at full power": vehicles sorted by arrival, each assigned to the first
compatible free charger at max power if grid capacity allows. Used only for comparison.

## AI Energy Advisor
Explains schedules, costs and bottlenecks with citations from ingested policy/tariff documents.
It may *propose* changes; every proposal is validated by the deterministic Java engine before
anything changes. The AI has no write access to data and no ability to bypass authorization.
