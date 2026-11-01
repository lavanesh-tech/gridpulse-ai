# Benchmarks

All results are produced by `./gradlew benchmark` (`SchedulingBenchmark`) on **seeded synthetic data**.
They describe algorithm behaviour on generated scenarios, **not** real-world savings.

Raw output: [`benchmarks/results/scheduling-latest.json`](benchmarks/results/scheduling-latest.json) and `.csv`.

## Methodology
- **Data:** `SyntheticScenarioGenerator`, seed 42. 16 h horizon, 15-min slots. Grid connections capped at 55% of
  their chargers' combined rating so capacity is a real constraint; ~5% of chargers out of service.
- **Scenarios:** small (50 vehicles, 16 chargers), medium (500 vehicles, 120 chargers), large (2,000 vehicles, 480 chargers).
- **Algorithms:** `baseline-fcfs` (arrival order, full power, contiguous) vs `priority-slack` (priority + least slack,
  power modulation, non-contiguous slots). Same problem object for both.
- **Correctness:** every schedule is checked by `ScheduleValidator`; `hardConstraintViolations` must be 0.
- **Timing:** 3 warm-up runs, then 10 measured runs; median and p95 wall-clock of `schedule()` only.
  Single JVM, no isolation from other processes, so treat timings as indicative.
- **Environment:** Java version, OS and CPU count are recorded in the JSON.

## Metrics
| Metric | Definition |
|---|---|
| deadlineSuccessRate | vehicles that reach target SoC before departure ÷ vehicles needing any charge |
| chargerUtilization | occupied charger-minutes ÷ (available chargers × horizon minutes) |
| energyDeliveredKwh | Σ session power × duration |
| hardConstraintViolations | count from the independent validator |

## Results
See the JSON/CSV committed with each milestone. Not yet covered (later milestones): electricity cost and
cost delta vs baseline (M4), API p50/p95/p99 (M7, k6), DB query timings (M7), test coverage (JaCoCo report).
