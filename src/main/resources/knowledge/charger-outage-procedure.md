# Charger Maintenance and Outage Procedure (synthetic sample)

This procedure is invented for the GridPulse AI demo.

## Reporting an outage
When a station stops working, the on-duty operator marks the station offline in GridPulse. Chargers at an offline station are excluded from new reservations and from generated schedules until the station is restored.

## Connector compatibility
Each charger has one connector type, such as CCS1, NACS or J1772. A vehicle can only be assigned to a charger with a matching connector. Adapters are not permitted at the depot for safety reasons.

## Restoring service
After repair, a technician confirms the charger works and the operator restores the station. Restored chargers become available to the scheduler for the next planning run.
