package com.gridpulse.scheduling;

import com.gridpulse.shared.domain.PowerKw;

import java.util.ArrayList;
import java.util.List;

/**
 * Grid capacity analysis for one grid connection: planned load per slot (including existing bookings),
 * the peak, and the bottleneck slots where load is at or above 95% of capacity.
 */
public record GridLoadProfile(String gridId, PowerKw capacity, List<PowerKw> loadBySlot, PowerKw peak,
                              double peakUtilization, List<Integer> bottleneckSlots) {
    public static final double BOTTLENECK_THRESHOLD = 0.95;

    public static List<GridLoadProfile> of(SchedulingProblem p, ChargingSchedule schedule) {
        CapacityLedger ledger = new CapacityLedger(p); // pre-loaded with fixed bookings
        java.util.Map<String, ChargerSpec> chargers = new java.util.HashMap<>();
        p.chargers().forEach(c -> chargers.put(c.chargerId(), c));
        java.util.Map<String, long[]> extra = new java.util.HashMap<>();
        p.grids().forEach(g -> extra.put(g.gridId(), new long[p.slotCount()]));
        for (ScheduledSession s : schedule.sessions()) {
            ChargerSpec c = chargers.get(s.chargerId());
            int[] range = CapacityLedger.slotRange(p, s.window());
            if (c == null || range == null) continue;
            for (int slot = range[0]; slot <= range[1]; slot++) {
                extra.get(c.gridId())[slot] += Units.watts(s.power());
            }
        }
        List<GridLoadProfile> result = new ArrayList<>();
        for (GridLimit g : p.grids()) {
            long cap = Units.watts(g.capacity());
            List<PowerKw> loads = new ArrayList<>();
            List<Integer> bottlenecks = new ArrayList<>();
            long peak = 0;
            for (int slot = 0; slot < p.slotCount(); slot++) {
                long w = ledger.usedWatts(g.gridId(), slot) + extra.get(g.gridId())[slot];
                loads.add(Units.kw(w));
                peak = Math.max(peak, w);
                if (cap > 0 && w >= BOTTLENECK_THRESHOLD * cap) bottlenecks.add(slot);
            }
            result.add(new GridLoadProfile(g.gridId(), g.capacity(), loads, Units.kw(peak),
                    cap == 0 ? 0 : Math.round(10_000.0 * peak / cap) / 10_000.0, bottlenecks));
        }
        return result;
    }
}
