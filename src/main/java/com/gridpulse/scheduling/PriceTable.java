package com.gridpulse.scheduling;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

/** Resolves each grid's tariff to a price per slot, in micro-currency units per kWh (exact integers). */
final class PriceTable {
    private final Map<String, long[]> microsPerKwh = new HashMap<>();
    private final long[] zero;
    private final Map<String, int[]> cheapestOrder = new HashMap<>();

    PriceTable(SchedulingProblem p) {
        zero = new long[p.slotCount()];
        for (GridTariff t : p.tariffs()) {
            long[] prices = new long[p.slotCount()];
            for (int s = 0; s < p.slotCount(); s++) {
                LocalTime local = p.slotStart(s).atZone(t.zone()).toLocalTime();
                for (TariffRate r : t.rates()) {
                    if (r.covers(local)) {
                        prices[s] = r.pricePerKwh().movePointRight(6).longValueExact();
                        break;
                    }
                }
            }
            microsPerKwh.put(t.gridId(), prices);
        }
    }

    /**
     * All slot indexes of a grid ordered cheapest first, then latest first. The order does not depend on the vehicle,
     * so it is computed once per grid and filtered per vehicle instead of sorted per vehicle.
     */
    int[] slotsCheapestThenLatest(String gridId) {
        return cheapestOrder.computeIfAbsent(gridId, g -> {
            long[] prices = microsPerKwh.getOrDefault(g, zero);
            Integer[] order = new Integer[prices.length];
            for (int i = 0; i < order.length; i++) order[i] = i;
            java.util.Arrays.sort(order, (a, b) -> prices[a] != prices[b] ? Long.compare(prices[a], prices[b]) : Integer.compare(b, a));
            int[] result = new int[order.length];
            for (int i = 0; i < order.length; i++) result[i] = order[i];
            return result;
        });
    }

    long priceMicros(String gridId, int slot) {
        return microsPerKwh.getOrDefault(gridId, zero)[slot];
    }

    /** Cost in micro-currency of {@code watts} applied for {@code minutes}: kWh = W·min/60000, cost = kWh·price. */
    static long costMicros(long watts, long minutes, long priceMicrosPerKwh) {
        return Math.multiplyExact(Math.multiplyExact(watts, minutes), priceMicrosPerKwh) / 60_000;
    }
}
