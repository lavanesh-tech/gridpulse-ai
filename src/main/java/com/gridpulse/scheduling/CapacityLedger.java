package com.gridpulse.scheduling;

import com.gridpulse.shared.domain.TimeWindow;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks, per time slot, how much power each grid connection is already supplying, which vehicle occupies
 * each charger, and when vehicles are already booked. All allocation goes through here, so hard constraints
 * are checked at write time. Existing bookings are loaded first and planned around.
 */
final class CapacityLedger {
    private static final String BOOKED = "<existing booking>";

    private final int slots;
    private final Map<String, long[]> gridUsedWatts = new HashMap<>();
    private final Map<String, Long> gridCapacityWatts = new HashMap<>();
    private final Map<String, String[]> chargerOccupant = new HashMap<>();
    private final Map<String, String> chargerGrid = new HashMap<>();
    private final Map<String, boolean[]> vehicleBusy = new HashMap<>();

    CapacityLedger(SchedulingProblem p) {
        this.slots = p.slotCount();
        for (GridLimit g : p.grids()) {
            gridUsedWatts.put(g.gridId(), new long[slots]);
            gridCapacityWatts.put(g.gridId(), Units.watts(g.capacity()));
        }
        for (ChargerSpec c : p.chargers()) {
            chargerOccupant.put(c.chargerId(), new String[slots]);
            chargerGrid.put(c.chargerId(), c.gridId());
        }
        for (FixedBooking b : p.fixedBookings()) {
            int[] range = slotRange(p, b.window());
            if (range == null) continue;
            long w = Units.watts(b.power());
            for (int s = range[0]; s <= range[1]; s++) {
                String[] occ = chargerOccupant.get(b.chargerId());
                if (occ[s] == null) occ[s] = BOOKED;
                gridUsedWatts.get(chargerGrid.get(b.chargerId()))[s] += w;
                if (b.vehicleId() != null) {
                    vehicleBusy.computeIfAbsent(b.vehicleId(), k -> new boolean[slots])[s] = true;
                }
            }
        }
    }

    /** Inclusive slot range touched by the window (a partial overlap occupies the whole slot), or null. */
    static int[] slotRange(SchedulingProblem p, TimeWindow w) {
        if (!w.end().isAfter(p.horizonStart()) || !w.start().isBefore(p.horizonEnd())) return null;
        return new int[]{p.slotContaining(w.start()), p.slotContaining(w.end().minusNanos(1))};
    }

    boolean chargerFree(String chargerId, int slot) {
        return chargerOccupant.get(chargerId)[slot] == null;
    }

    boolean vehicleFree(String vehicleId, int slot) {
        boolean[] busy = vehicleBusy.get(vehicleId);
        return busy == null || !busy[slot];
    }

    long headroomWatts(String gridId, int slot) {
        return gridCapacityWatts.get(gridId) - gridUsedWatts.get(gridId)[slot];
    }

    void allocate(ChargerSpec charger, String vehicleId, int slot, long watts) {
        if (!chargerFree(charger.chargerId(), slot)) {
            throw new IllegalStateException("charger " + charger.chargerId() + " already occupied in slot " + slot);
        }
        if (!vehicleFree(vehicleId, slot)) {
            throw new IllegalStateException("vehicle " + vehicleId + " already booked in slot " + slot);
        }
        if (watts > headroomWatts(charger.gridId(), slot)) {
            throw new IllegalStateException("grid " + charger.gridId() + " capacity exceeded in slot " + slot);
        }
        chargerOccupant.get(charger.chargerId())[slot] = vehicleId;
        gridUsedWatts.get(charger.gridId())[slot] += watts;
    }

    long usedWatts(String gridId, int slot) {
        return gridUsedWatts.get(gridId)[slot];
    }
}
