package com.gridpulse.scheduling;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks, per time slot, how much power each grid connection is already supplying and which vehicle
 * occupies each charger. All allocation goes through here, so hard constraints are checked at write time.
 */
final class CapacityLedger {
    private final Map<String, long[]> gridUsedWatts = new HashMap<>();
    private final Map<String, Long> gridCapacityWatts = new HashMap<>();
    private final Map<String, String[]> chargerOccupant = new HashMap<>();

    CapacityLedger(SchedulingProblem p) {
        for (GridLimit g : p.grids()) {
            gridUsedWatts.put(g.gridId(), new long[p.slotCount()]);
            gridCapacityWatts.put(g.gridId(), Units.watts(g.capacity()));
        }
        for (ChargerSpec c : p.chargers()) {
            chargerOccupant.put(c.chargerId(), new String[p.slotCount()]);
        }
    }

    boolean chargerFree(String chargerId, int slot) {
        return chargerOccupant.get(chargerId)[slot] == null;
    }

    long headroomWatts(String gridId, int slot) {
        return gridCapacityWatts.get(gridId) - gridUsedWatts.get(gridId)[slot];
    }

    void allocate(ChargerSpec charger, String vehicleId, int slot, long watts) {
        if (!chargerFree(charger.chargerId(), slot)) {
            throw new IllegalStateException("charger " + charger.chargerId() + " already occupied in slot " + slot);
        }
        if (watts > headroomWatts(charger.gridId(), slot)) {
            throw new IllegalStateException("grid " + charger.gridId() + " capacity exceeded in slot " + slot);
        }
        chargerOccupant.get(charger.chargerId())[slot] = vehicleId;
        gridUsedWatts.get(charger.gridId())[slot] += watts;
    }
}
