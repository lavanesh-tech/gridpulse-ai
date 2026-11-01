package com.gridpulse.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gridpulse.scheduling.BaselineScheduler;
import com.gridpulse.scheduling.ChargingSchedule;
import com.gridpulse.scheduling.ChargingScheduler;
import com.gridpulse.scheduling.PrioritySlackScheduler;
import com.gridpulse.scheduling.ScheduleMetrics;
import com.gridpulse.scheduling.ScheduleValidator;
import com.gridpulse.scheduling.SchedulingProblem;
import com.gridpulse.scheduling.SyntheticScenarioGenerator;
import com.gridpulse.scheduling.Violation;

import java.io.IOException;
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

    private SchedulingBenchmark() {
    }

    public static void main(String[] args) throws IOException {
        Path outDir = Path.of(args.length > 0 ? args[0] : "benchmarks/results");
        Files.createDirectories(outDir);
        List<ChargingScheduler> schedulers = List.of(new BaselineScheduler(), new PrioritySlackScheduler());
        ScheduleValidator validator = new ScheduleValidator();
        List<Map<String, Object>> rows = new ArrayList<>();

        for (Scenario sc : SCENARIOS) {
            SchedulingProblem problem = SyntheticScenarioGenerator.generate(sc.seed(), sc.vehicles(), sc.grids(), sc.chargersPerGrid());
            for (ChargingScheduler scheduler : schedulers) {
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
                row.put("hardConstraintViolations", m.violations());
                row.put("chargerUtilization", round(m.chargerUtilization()));
                row.put("energyRequiredKwh", m.energyRequired().value());
                row.put("energyDeliveredKwh", m.energyDelivered().value());
                row.put("computeMsMedian", nanos[MEASURED_RUNS / 2] / 1e6);
                row.put("computeMsP95", nanos[(int) Math.ceil(0.95 * MEASURED_RUNS) - 1] / 1e6);
                rows.add(row);
                System.out.printf("%-7s %-15s met %4d/%-4d (%.1f%%) violations=%d util=%.1f%% median=%.2f ms%n",
                        sc.name(), m.algorithm(), m.deadlinesMet(), m.vehiclesNeedingCharge(),
                        100 * m.deadlineSuccessRate(), m.violations(), 100 * m.chargerUtilization(),
                        nanos[MEASURED_RUNS / 2] / 1e6);
            }
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("generatedAt", Instant.now().toString());
        report.put("note", "Synthetic data only. Not real-world business results.");
        report.put("javaVersion", System.getProperty("java.version"));
        report.put("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        report.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        report.put("warmupRuns", WARMUP_RUNS);
        report.put("measuredRuns", MEASURED_RUNS);
        report.put("results", rows);
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(outDir.resolve("scheduling-latest.json").toFile(), report);

        StringBuilder csv = new StringBuilder(String.join(",", rows.get(0).keySet())).append('\n');
        for (Map<String, Object> row : rows) {
            csv.append(String.join(",", row.values().stream().map(String::valueOf).toList())).append('\n');
        }
        Files.writeString(outDir.resolve("scheduling-latest.csv"), csv);
        System.out.println("Wrote " + outDir.toAbsolutePath());
    }

    private static double round(double v) {
        return Math.round(v * 10_000) / 10_000.0;
    }
}
