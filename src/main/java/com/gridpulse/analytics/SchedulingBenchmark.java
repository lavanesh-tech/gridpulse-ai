package com.gridpulse.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gridpulse.scheduling.BaselineScheduler;
import com.gridpulse.scheduling.ChargingSchedule;
import com.gridpulse.scheduling.ChargingScheduler;
import com.gridpulse.scheduling.PriorityOutcome;
import com.gridpulse.scheduling.PrioritySlackScheduler;
import com.gridpulse.scheduling.ScheduleMetrics;
import com.gridpulse.scheduling.ScheduleValidator;
import com.gridpulse.scheduling.SchedulingProblem;
import com.gridpulse.scheduling.SyntheticScenarioGenerator;
import com.gridpulse.scheduling.Violation;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reproducible baseline-vs-optimized benchmark on seeded synthetic data.
 * Run: ./gradlew benchmark  → benchmarks/results/scheduling-latest.{json,csv}
 * Every number in the output is measured by this program on the machine that ran it.
 * Part 1: seed 42 at three sizes, with timing. Part 2: aggregate over seeds 1..20 at two sizes (no timing),
 * so conclusions don't rest on a single seed.
 */
public final class SchedulingBenchmark {

    record Scenario(String name, long seed, int vehicles, int grids, int chargersPerGrid) {
    }

    static final List<Scenario> SCENARIOS = List.of(
            new Scenario("small", 42, 50, 2, 8),
            new Scenario("medium", 42, 500, 6, 20),
            new Scenario("large", 42, 2000, 12, 40));
    static final int WARMUP_RUNS = 3;
    static final int MEASURED_RUNS = 10;
    static final int AGGREGATE_SEEDS = 20;

    private SchedulingBenchmark() {
    }

    static List<ChargingScheduler> schedulers() {
        return List.of(new BaselineScheduler(),
                new PrioritySlackScheduler(PrioritySlackScheduler.Objective.DEADLINES_FIRST),
                new PrioritySlackScheduler(PrioritySlackScheduler.Objective.COST_FIRST));
    }

    public static void main(String[] args) throws IOException {
        Path outDir = Path.of(args.length > 0 ? args[0] : "benchmarks/results");
        Files.createDirectories(outDir);
        ScheduleValidator validator = new ScheduleValidator();
        List<Map<String, Object>> rows = new ArrayList<>();

        for (Scenario sc : SCENARIOS) {
            SchedulingProblem problem = SyntheticScenarioGenerator.generate(sc.seed(), sc.vehicles(), sc.grids(), sc.chargersPerGrid());
            BigDecimal baselineCost = null;
            for (ChargingScheduler scheduler : schedulers()) {
                for (int i = 0; i < WARMUP_RUNS; i++) {
                    scheduler.schedule(problem);
                }
                long[] nanos = new long[MEASURED_RUNS];
                ChargingSchedule result = null;
                for (int i = 0; i < MEASURED_RUNS; i++) {
                    long t0 = System.nanoTime();
                    result = scheduler.schedule(problem);
                    nanos[i] = System.nanoTime() - t0;
                }
                Arrays.sort(nanos);
                List<Violation> violations = validator.validate(problem, result);
                ScheduleMetrics m = ScheduleMetrics.of(problem, result, violations);
                if (baselineCost == null) baselineCost = m.cost().totalCost();

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("scenario", sc.name());
                row.put("seed", sc.seed());
                row.put("vehicles", m.vehicles());
                row.put("chargers", problem.chargers().size());
                row.put("gridConnections", problem.grids().size());
                row.put("algorithm", m.algorithm());
                row.put("sessions", m.sessions());
                row.put("vehiclesNeedingCharge", m.vehiclesNeedingCharge());
                row.put("deadlinesMet", m.deadlinesMet());
                row.put("deadlineSuccessRate", round(m.deadlineSuccessRate()));
                for (PriorityOutcome o : m.byPriority()) {
                    row.put("p" + o.priority() + "SuccessRate", round(o.successRate()));
                }
                row.put("hardConstraintViolations", m.violations());
                row.put("chargerUtilization", round(m.chargerUtilization()));
                row.put("energyRequiredKwh", m.energyRequired().value());
                row.put("energyDeliveredKwh", m.energyDelivered().value());
                row.put("estimatedCost", m.cost().totalCost());
                row.put("averagePricePerKwh", m.cost().averagePricePerKwh());
                row.put("costVsBaselinePct", pctChange(m.cost().totalCost(), baselineCost));
                row.put("computeMsMedian", nanos[MEASURED_RUNS / 2] / 1e6);
                row.put("computeMsP95", nanos[(int) Math.ceil(0.95 * MEASURED_RUNS) - 1] / 1e6);
                rows.add(row);
                System.out.printf("%-7s %-19s met %4d/%-4d (%.1f%%) viol=%d cost=%s (%s%%) median=%.2f ms%n",
                        sc.name(), m.algorithm(), m.deadlinesMet(), m.vehiclesNeedingCharge(),
                        100 * m.deadlineSuccessRate(), m.violations(), m.cost().totalCost(),
                        pctChange(m.cost().totalCost(), baselineCost), nanos[MEASURED_RUNS / 2] / 1e6);
            }
        }

        List<Map<String, Object>> aggregate = aggregate(validator);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("generatedAt", Instant.now().toString());
        report.put("note", "Synthetic data and an invented tariff. Not real-world business results.");
        report.put("javaVersion", System.getProperty("java.version"));
        report.put("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        report.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        report.put("warmupRuns", WARMUP_RUNS);
        report.put("measuredRuns", MEASURED_RUNS);
        report.put("results", rows);
        report.put("aggregateSeeds1to20", aggregate);
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(outDir.resolve("scheduling-latest.json").toFile(), report);

        StringBuilder csv = new StringBuilder(String.join(",", rows.get(0).keySet())).append('\n');
        for (Map<String, Object> row : rows) {
            List<String> values = new ArrayList<>();
            for (String key : rows.get(0).keySet()) values.add(String.valueOf(row.getOrDefault(key, "")));
            csv.append(String.join(",", values)).append('\n');
        }
        Files.writeString(outDir.resolve("scheduling-latest.csv"), csv);
        System.out.println("Wrote " + outDir.toAbsolutePath());
    }

    /** Totals over seeds 1..20 × {500 vehicles, 2000 vehicles}; every schedule must validate. */
    private static List<Map<String, Object>> aggregate(ScheduleValidator validator) {
        int[][] sizes = {{500, 6, 20}, {2000, 12, 40}};
        Map<String, long[]> counts = new LinkedHashMap<>();   // met, needing, p2met, p2need, p1met, p1need, p0met, p0need, violations
        Map<String, BigDecimal> cost = new LinkedHashMap<>();
        Map<String, BigDecimal> energy = new LinkedHashMap<>();
        for (int[] size : sizes) {
            for (long seed = 1; seed <= AGGREGATE_SEEDS; seed++) {
                SchedulingProblem p = SyntheticScenarioGenerator.generate(seed, size[0], size[1], size[2]);
                for (ChargingScheduler s : schedulers()) {
                    ChargingSchedule r = s.schedule(p);
                    List<Violation> v = validator.validate(p, r);
                    ScheduleMetrics m = ScheduleMetrics.of(p, r, v);
                    long[] c = counts.computeIfAbsent(s.name(), k -> new long[9]);
                    c[0] += m.deadlinesMet();
                    c[1] += m.vehiclesNeedingCharge();
                    for (PriorityOutcome o : m.byPriority()) {
                        int idx = 2 + 2 * (2 - Math.min(2, o.priority()));
                        c[idx] += o.met();
                        c[idx + 1] += o.needingCharge();
                    }
                    c[8] += v.size();
                    cost.merge(s.name(), m.cost().totalCost(), BigDecimal::add);
                    energy.merge(s.name(), m.cost().energy().value(), BigDecimal::add);
                }
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        BigDecimal baseline = cost.get(BaselineScheduler.NAME);
        System.out.println("Aggregate over seeds 1.." + AGGREGATE_SEEDS + " x {500, 2000} vehicles:");
        for (Map.Entry<String, long[]> e : counts.entrySet()) {
            long[] c = e.getValue();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("algorithm", e.getKey());
            row.put("scenarios", sizes.length * AGGREGATE_SEEDS);
            row.put("deadlinesMet", c[0]);
            row.put("vehiclesNeedingCharge", c[1]);
            row.put("deadlineSuccessRate", round((double) c[0] / c[1]));
            row.put("p2SuccessRate", round((double) c[2] / c[3]));
            row.put("p1SuccessRate", round((double) c[4] / c[5]));
            row.put("p0SuccessRate", round((double) c[6] / c[7]));
            row.put("hardConstraintViolations", c[8]);
            row.put("estimatedCost", cost.get(e.getKey()));
            row.put("averagePricePerKwh", cost.get(e.getKey()).divide(energy.get(e.getKey()), 4, RoundingMode.HALF_UP));
            row.put("costVsBaselinePct", pctChange(cost.get(e.getKey()), baseline));
            out.add(row);
            System.out.printf("  %-19s met %d/%d (%.2f%%)  p2 %.2f%% p1 %.2f%% p0 %.2f%%  violations=%d  %s/kWh (%s%% vs baseline)%n",
                    e.getKey(), c[0], c[1], 100.0 * c[0] / c[1], 100.0 * c[2] / c[3], 100.0 * c[4] / c[5],
                    100.0 * c[6] / c[7], c[8], row.get("averagePricePerKwh"), row.get("costVsBaselinePct"));
        }
        return out;
    }

    private static BigDecimal pctChange(BigDecimal value, BigDecimal baseline) {
        if (baseline == null || baseline.signum() == 0) return BigDecimal.ZERO;
        return value.subtract(baseline).multiply(BigDecimal.valueOf(100)).divide(baseline, 1, RoundingMode.HALF_UP);
    }

    private static double round(double v) {
        return Math.round(v * 10_000) / 10_000.0;
    }
}
