# Grid Connection Agreement Summary (synthetic sample)

This summary is invented for the GridPulse AI demo.

## Capacity limit
Each depot grid connection has a contracted maximum import capacity in kilowatts. The total power drawn by all chargers on that connection must never exceed this limit at any moment. Exceeding the limit can trip the main breaker and may incur penalty fees from the utility.

## Curtailment
During system emergencies the utility may ask the depot to reduce its import capacity with at least 30 minutes notice. When a curtailment notice is received, the operator enters the reduced capacity in GridPulse so the remaining charging plan is recalculated within the new limit.

## Responsibilities
The fleet operator is responsible for configuring chargers and capacity limits correctly. GridPulse enforces the configured limit when reservations are made and when schedules are generated.
