package com.gridpulse.scheduling;

import com.gridpulse.shared.domain.EnergyKwh;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Estimated electricity cost of a schedule under the problem's tariffs. Each session is split by the slots it
 * overlaps and priced at that slot's rate, so it is correct for any session, not only slot-aligned ones.
 * Demand charges, taxes and fixed fees are not modelled.
 */
public record ScheduleCost(BigDecimal totalCost, EnergyKwh energy, BigDecimal averagePricePerKwh) {

    public static ScheduleCost of(SchedulingProblem p, ChargingSchedule schedule) {
        PriceTable prices = new PriceTable(p);
        Map<String, String> gridOf = new HashMap<>();
        p.chargers().forEach(c -> gridOf.put(c.chargerId(), c.gridId()));
        long costMicros = 0;
        long wattMinutes = 0;
        for (ScheduledSession s : schedule.sessions()) {
            String grid = gridOf.get(s.chargerId());
            if (grid == null) continue;
            long watts = Units.watts(s.power());
            int[] range = CapacityLedger.slotRange(p, s.window());
            if (range == null) continue;
            for (int slot = range[0]; slot <= range[1]; slot++) {
                Instant from = max(s.window().start(), p.slotStart(slot));
                Instant to = min(s.window().end(), p.slotStart(slot + 1));
                long minutes = Duration.between(from, to).toMinutes();
                if (minutes <= 0) continue;
                wattMinutes += watts * minutes;
                costMicros += PriceTable.costMicros(watts, minutes, prices.priceMicros(grid, slot));
            }
        }
        BigDecimal total = BigDecimal.valueOf(costMicros, 6).setScale(2, RoundingMode.HALF_UP);
        EnergyKwh energy = Units.kwh(wattMinutes);
        BigDecimal avg = energy.value().signum() == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(costMicros, 6).divide(energy.value(), 4, RoundingMode.HALF_UP);
        return new ScheduleCost(total, energy, avg);
    }

    private static Instant max(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }
}
