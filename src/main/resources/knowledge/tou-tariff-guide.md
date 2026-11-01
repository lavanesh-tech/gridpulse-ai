# Time-of-Use Tariff Guide (synthetic sample)

This tariff is invented for the GridPulse AI demo. Prices are not real utility rates.

## Price bands
Electricity at the depot is billed per kilowatt-hour with three time-of-use bands in local time. The peak band runs from 16:00 to 21:00 at 0.32 per kWh. The off-peak band runs from 21:00 to 06:00 at 0.11 per kWh. The shoulder band runs from 06:00 to 16:00 at 0.18 per kWh.

## What this means for charging
Vehicles that return between 16:00 and 21:00 and start charging immediately pay the peak rate. Delaying charging until after 21:00 moves energy into the off-peak band, which costs about one third of the peak price. Charging after 06:00 is billed at the shoulder rate.

## What is not included
Demand charges based on the highest 15-minute power draw in a billing month, taxes, and fixed monthly fees are not part of this guide and are not modelled by GridPulse cost estimates.
