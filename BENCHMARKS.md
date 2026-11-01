# Benchmarks

All results come from `./gradlew benchmark` (`SchedulingBenchmark`) on **seeded synthetic data with an invented
tariff**. They describe algorithm behaviour on generated scenarios, **not** real-world savings.

Raw output: [`benchmarks/results/scheduling-latest.json`](benchmarks/results/scheduling-latest.json) and `.csv`.

## Methodology
- **Data:** `SyntheticScenarioGenerator`. 16 h overnight horizon (18:00–10:00 New York time), 15-min slots.
  Each grid connection is capped at 55% of its chargers' combined rating, so capacity really binds;
  ~5% of chargers are out of service. Priorities: ~20% priority 2 (urgent), ~40% priority 1, ~40% priority 0.
- **Tariff (invented):** peak 16:00–21:00 at 0.32, off-peak 21:00–06:00 at 0.11, shoulder 06:00–16:00 at 0.18 per kWh.
  No demand charges, taxes or fixed fees are modelled.
- **Algorithms** (identical input for all three):
  - `baseline-fcfs` — arrival order, full power, contiguous, earliest slot.
  - `priority-slack` — *deadlines first*: priority, then least slack; latest slots first; power modulation.
  - `priority-slack-cost` — *cost first*: same ordering of vehicles; cheapest slots first.
- **Correctness:** every schedule is checked by the independent `ScheduleValidator`; violations must be 0.
- **Outcome metrics are deterministic** — anyone running the benchmark gets the same deadline, cost and
  violation numbers. **Timings** depend on the machine: 3 warm-up + 10 measured runs, median/p95 wall-clock of
  `schedule()` only, single JVM, not isolated from other processes. Environment is recorded in the JSON.

## Main result — aggregate over 40 scenarios (seeds 1–20 × {500, 2,000} vehicles; 50,000 vehicles needing charge)
| Algorithm | On time | Urgent (p2) | p1 | p0 | Violations | Avg price/kWh | Cost vs baseline |
|---|---|---|---|---|---|---|---|
| baseline-fcfs | 96.69% | 96.41% | 96.64% | 96.88% | 0 | 0.2337 | — |
| **priority-slack** (default) | **97.08%** | **99.62%** | 99.34% | 93.51% | 0 | 0.1617 | **−30.4%** |
| priority-slack-cost | 96.14% | 99.62% | 98.96% | 91.52% | 0 | 0.1127 | −51.9% |

### Reading it honestly
- **Deadlines-first** meets more deadlines than the baseline overall (+0.39 points) and makes urgent vehicles
  almost always on time (96.4% → 99.6%), at ~30% lower estimated energy cost on this tariff.
  **The price:** priority-0 vehicles do worse (96.9% → 93.5%). That is the intended policy, not a free lunch.
- **Cost-first** cuts estimated cost by about half but meets *fewer* deadlines than the baseline overall,
  because everyone competes for the same cheap off-peak slots. Use it only when deadlines are loose.
- The cost advantage mostly comes from the baseline charging at full power in the 18:00–21:00 peak right after
  arrival. A flat tariff would remove most of it.
- **Recovery risk:** deadlines-first charges as late as possible, so a charger failure late in the night leaves
  little time to recover (`ScheduleRecalculatorTest.lateOutageInterruptsTheSessionAndReportsTheMiss`).
  The recalculator reports such misses explicitly.
- All three are greedy heuristics; none is proven optimal. An exact solver (MILP/CP-SAT) is out of scope.

## Single-seed detail (seed 42) — Milestone 4
| Scenario | Algorithm | On time | Cost | vs baseline |
|---|---|---|---|---|
| small (50) | baseline-fcfs | 50/50 | 604.42 | — |
| small (50) | priority-slack | 50/50 | 367.00 | −39.3% |
| small (50) | priority-slack-cost | 50/50 | 291.35 | −51.8% |
| medium (500) | baseline-fcfs | 461/500 | 6,330.95 | — |
| medium (500) | priority-slack | 466/500 | 4,295.26 | −32.2% |
| medium (500) | priority-slack-cost | 459/500 | 3,000.76 | −52.6% |
| large (2,000) | baseline-fcfs | 1959/2000 | 25,409.19 | — |
| large (2,000) | priority-slack | 1992/2000 | 17,831.67 | −29.8% |
| large (2,000) | priority-slack-cost | 1935/2000 | 12,297.43 | −51.6% |

Compute times for this run are in the committed JSON (machine-specific).

## History of this benchmark
| Milestone | Finding | Action |
|---|---|---|
| M2 | First optimizer met *fewer* deadlines than baseline (91.4% vs 92.2% medium, 97.2% vs 98.0% large) | Added per-priority metrics; found it did favour urgent vehicles but lost on throughput |
| M4 | Experiments: charger-choice tweaks → no change; **latest-slots-first → +0.4–0.7 points**; cheapest-first → cheaper but fewer deadlines met | Made the objective explicit (deadlines-first default, cost-first optional) |
| M4 | Optimizer ~8× slower than baseline after adding cost ordering (boxed sort per vehicle×charger) | Precomputed per-grid slot order: 210 → 92 ms at 2,000 vehicles, identical results |

## Not yet measured (later milestones)
API p50/p95/p99 latency and DB query timings (M7, k6), concurrent reservation conflict rates under load (M7),
test coverage (JaCoCo report published in CI, M8).
