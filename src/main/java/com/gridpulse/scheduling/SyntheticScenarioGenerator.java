package com.gridpulse.scheduling;

import com.gridpulse.shared.domain.ConnectorType;
import com.gridpulse.shared.domain.EnergyKwh;
import com.gridpulse.shared.domain.PowerKw;
import com.gridpulse.shared.domain.StateOfCharge;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Seeded synthetic overnight-depot scenarios. Same seed and sizes always produce the same problem.
 * All values are invented for testing and benchmarking; they do not describe any real fleet.
 * <ul>
 *   <li>Horizon: 16 h in 15-minute slots.</li>
 *   <li>Each grid connection is capped at 55% of its chargers' combined rating, so capacity really binds.</li>
 *   <li>About 5% of chargers are out of service (outage simulation).</li>
 * </ul>
 */
public final class SyntheticScenarioGenerator {
    public static final Instant HORIZON_START = Instant.parse("2026-01-05T23:00:00Z");
    public static final int SLOT_MINUTES = 15;
    public static final int SLOT_COUNT = 64;
    public static final int GRID_CAPACITY_PERCENT = 55;

    private SyntheticScenarioGenerator() {
    }

    public static SchedulingProblem generate(long seed, int vehicles, int gridConnections, int chargersPerGrid) {
        Random rnd = new Random(seed);
        List<ChargerSpec> chargers = new ArrayList<>();
        List<GridLimit> grids = new ArrayList<>();

        for (int g = 0; g < gridConnections; g++) {
            String gridId = "grid-%02d".formatted(g);
            long totalWatts = 0;
            for (int c = 0; c < chargersPerGrid; c++) {
                double x = rnd.nextDouble();
                ConnectorType type;
                long watts;
                if (x < 0.50) {
                    type = ConnectorType.CCS1;
                    watts = rnd.nextBoolean() ? 50_000 : 150_000;
                } else if (x < 0.85) {
                    type = ConnectorType.NACS;
                    watts = rnd.nextBoolean() ? 150_000 : 250_000;
                } else {
                    type = ConnectorType.J1772;
                    watts = 11_500;
                }
                boolean available = rnd.nextDouble() >= 0.05;
                chargers.add(new ChargerSpec("%s-c%03d".formatted(gridId, c), gridId, type, Units.kw(watts), available));
                totalWatts += watts;
            }
            grids.add(new GridLimit(gridId, Units.kw(totalWatts * GRID_CAPACITY_PERCENT / 100)));
        }

        Instant horizonEnd = HORIZON_START.plus(Duration.ofMinutes((long) SLOT_MINUTES * SLOT_COUNT));
        List<ChargingRequest> requests = new ArrayList<>();
        for (int v = 0; v < vehicles; v++) {
            double x = rnd.nextDouble();
            ConnectorType type;
            double rateKw;
            if (x < 0.55) {
                type = ConnectorType.CCS1;
                rateKw = 50 + rnd.nextInt(151);
            } else if (x < 0.90) {
                type = ConnectorType.NACS;
                rateKw = 100 + rnd.nextInt(151);
            } else {
                type = ConnectorType.J1772;
                rateKw = 11.5;
            }
            int batteryKwh = 60 + rnd.nextInt(76);
            int soc = 10 + rnd.nextInt(41);
            int target = Math.max(soc, 80 + rnd.nextInt(16));
            Instant arrival = HORIZON_START.plus(Duration.ofMinutes(rnd.nextInt(240)));
            Instant departure = arrival.plus(Duration.ofMinutes(360 + rnd.nextInt(481)));
            if (departure.isAfter(horizonEnd)) {
                departure = horizonEnd;
            }
            int priority = rnd.nextDouble() < 0.2 ? 2 : (rnd.nextDouble() < 0.5 ? 1 : 0);
            requests.add(new ChargingRequest("veh-%05d".formatted(v), type, EnergyKwh.of(batteryKwh),
                    new StateOfCharge(soc), new StateOfCharge(target), PowerKw.of(rateKw), arrival, departure, priority));
        }
        return new SchedulingProblem(requests, chargers, grids, HORIZON_START, SLOT_MINUTES, SLOT_COUNT);
    }
}
