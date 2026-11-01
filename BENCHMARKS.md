# Benchmarks

No results yet. Numbers appear here only after a reproducible run (Milestone 2 onward).

## Methodology (planned)
- Seeded synthetic datasets (seed recorded with every result), sizes e.g. 50 / 500 / 5,000 vehicles.
- Baseline (FCFS full-power) vs optimized scheduler on the identical dataset.
- Metrics: vehicles processed, sessions generated, compute time (JMH or repeated warm runs, median + p95),
  deadline success rate, grid violations (must be 0 for optimized), charger utilization, estimated cost,
  cost delta vs baseline, API p50/p95/p99 (k6), reservation conflict rates under concurrency.
- Hardware, JVM version and commit SHA recorded with each run. Raw output stored as CSV/JSON in `benchmarks/results/`.
