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

## Results — Milestone 2 (commit 1688258, author's Mac, JDK 21.0.12, seed 42)
| Scenario | Algorithm | Deadlines met | Success rate | Violations | Charger utilization | Median compute |
|---|---|---|---|---|---|---|
| small (50) | baseline-fcfs | 50/50 | 100.0% | 0 | 19.6% | 0.55 ms |
| small (50) | priority-slack | 50/50 | 100.0% | 0 | 17.7% | 0.44 ms |
| medium (500) | baseline-fcfs | 461/500 | 92.2% | 0 | 29.2% | 1.83 ms |
| medium (500) | priority-slack | 457/500 | 91.4% | 0 | 26.9% | 4.28 ms |
| large (2,000) | baseline-fcfs | 1959/2000 | 98.0% | 0 | 30.4% | 18.52 ms |
| large (2,000) | priority-slack | 1944/2000 | 97.2% | 0 | 28.9% | 42.69 ms |

### Honest reading
- Both algorithms satisfy every hard constraint on every scenario.
- **The priority-slack scheduler does not beat the baseline on overall deadline success here** (−0.8 points on
  medium and large) and is ~2× slower. It serves high-priority vehicles first by design, which can cost total
  count; the current metric does not yet measure priority-weighted success, so the trade-off is not visible.
- It does win the targeted case it was designed for (`SchedulerTest.optimizedMeetsBothDeadlinesByServingLeastSlackFirst`),
  but that is a hand-built scenario, not evidence of general superiority.
- Action for Milestone 4: add per-priority success metrics, then improve the optimizer (e.g. choose charger by
  least wasted capacity instead of earliest finish, repair pass for missed vehicles) and re-measure.

Later milestones add: electricity cost and
cost delta vs baseline (M4), API p50/p95/p99 (M7, k6), DB query timings (M7), test coverage (JaCoCo report).
