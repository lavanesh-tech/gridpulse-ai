# Depot Charging Policy (synthetic sample)

This document is invented for the GridPulse AI demo. It does not describe any real company.

## Purpose
This policy sets how vehicles are prioritised when depot charging capacity is limited overnight. The goal is that every vehicle leaves on time with enough charge for its route.

## Priority levels
Priority 2 is used for vehicles assigned to time-critical routes such as medical deliveries and first-shift departures. Priority 1 is the default for scheduled routes. Priority 0 is for spare vehicles and vehicles without a route assignment for the next shift. When capacity runs short, priority 2 vehicles are charged first, then priority 1, then priority 0.

## Target state of charge
The standard departure target is 80% state of charge. Charging above 90% is only allowed when the route planner requests it for a long route, because sustained high state of charge accelerates battery degradation. A vehicle that cannot reach its target must be reported to the shift supervisor before 05:00.

## Interrupted sessions
If a charging session is interrupted by a charger fault or station outage, the operator records the interruption with a reason in GridPulse and the schedule is recalculated for the remaining vehicles. Vehicles moved to another charger keep their original priority.
