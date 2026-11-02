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

## Retrieval quality (Milestone 5)
Bundled evaluation: 14 questions over 4 synthetic documents (17 chunks): 10 keyword questions + 4 deliberate paraphrases.
| Embedder | hit@3 | MRR | Notes |
|---|---|---|---|
| offline lexical (`lexical-hash-v1`) | 0.9286 | 0.881 | all 10 keyword questions rank 1; misses "cheapest… overnight" (off-peak), ranks "who gets juice first" 3rd |
| OpenAI `text-embedding-3-small` | 1.0 | 0.9524 | all 14 in the top 3, including the off-peak paraphrase; only "who gets juice first" ranks 3rd. Measured 2026-11-01 on the author's Mac via `scripts/evaluate-openai.sh` (raw: `benchmarks/results/retrieval-openai.json`) |

**Relevance threshold (lexical).** Hashing 1536 buckets causes word collisions that give unrelated text a small score.
Over 5,000 random off-topic queries: 14.0% scored above 0.10, 1.06% above 0.15, 0.12% above 0.20 (max 0.217). All 10
keyword questions score ≥ 0.243, so the threshold is **0.20**; two paraphrases (0.183, 0.109) are then refused — the honest
outcome for an embedder that cannot match meaning. Found by `AdvisorIT` failing at the first threshold (0.10).

The lexical numbers were measured offline with the same chunker and embedder (brute-force cosine) and are re-checked
against pgvector by `AdvisorIT`. Fourteen questions is a smoke test, not a benchmark of semantic quality.

## API load test (Milestone 7)
k6 1.0.0 (Docker) against a throwaway stack on the author's laptop (Apple M5 Pro, 15 cores; app, Postgres and k6 on the
same machine), 2026-11-01. Open model, 60 s: 50 list requests/s plus 5 schedule previews/s, where each preview plans a
synthetic depot of 100 vehicles on 20 chargers (PRIORITY_SLACK). Raw: `benchmarks/results/load-test-latest.json`.

| Workload | p50 | p95 | max | Errors |
|---|---|---|---|---|
| Reads (fleets, vehicles) | 5.3 ms | 8.8 ms | 28.3 ms | 0% |
| Schedule preview, 100 vehicles | 13.9 ms | 18.4 ms | 56.4 ms | 0% |

All thresholds passed (errors < 1%, read p95 < 250 ms, plan p95 < 1500 ms), and every preview had zero constraint
violations. This is a single-machine run at a modest fixed rate: it shows latency headroom, not maximum throughput or
production capacity. Request counts were not captured in this run (fixed in the script for later runs).

## Not yet measured (later milestones)
DB query timings, concurrent reservation conflict rates under load, maximum throughput, JaCoCo report published in CI.
Test coverage at M7: gate at 80% instructions / 70% branches (measured 82% / 75% before M7).
