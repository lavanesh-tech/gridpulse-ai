package com.gridpulse.scheduling;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Everything the scheduler needs. Time is split into equal slots starting at {@code horizonStart};
 * slot i covers [start + i*slotMinutes, start + (i+1)*slotMinutes).
 */
public record SchedulingProblem(
        List<ChargingRequest> requests,
        List<ChargerSpec> chargers,
        List<GridLimit> grids,
        Instant horizonStart,
        int slotMinutes,
        int slotCount) {

    public SchedulingProblem {
        requests = List.copyOf(requests);
        chargers = List.copyOf(chargers);
        grids = List.copyOf(grids);
        Objects.requireNonNull(horizonStart, "horizonStart");
        if (slotMinutes <= 0 || slotCount <= 0) {
            throw new IllegalArgumentException("slotMinutes and slotCount must be > 0");
        }
        Set<String> gridIds = new HashSet<>();
        for (GridLimit g : grids) {
            if (!gridIds.add(g.gridId())) throw new IllegalArgumentException("duplicate grid " + g.gridId());
        }
        Set<String> chargerIds = new HashSet<>();
        for (ChargerSpec c : chargers) {
            if (!chargerIds.add(c.chargerId())) throw new IllegalArgumentException("duplicate charger " + c.chargerId());
            if (!gridIds.contains(c.gridId())) throw new IllegalArgumentException("unknown grid " + c.gridId());
        }
        Set<String> vehicleIds = new HashSet<>();
        for (ChargingRequest r : requests) {
            if (!vehicleIds.add(r.vehicleId())) throw new IllegalArgumentException("duplicate vehicle " + r.vehicleId());
        }
    }

    public Instant slotStart(int slot) {
        return horizonStart.plus(Duration.ofMinutes((long) slot * slotMinutes));
    }

    public Instant horizonEnd() {
        return slotStart(slotCount);
    }

    /** Index of the first slot that starts at or after {@code t} (vehicle must be plugged in for the whole slot). */
    int firstSlotAtOrAfter(Instant t) {
        long seconds = Duration.between(horizonStart, t).getSeconds();
        if (seconds <= 0) return 0;
        long slotSeconds = slotMinutes * 60L;
        return (int) Math.min(slotCount, (seconds + slotSeconds - 1) / slotSeconds);
    }

    /** Number of slots that end at or before {@code t}; used as an exclusive upper bound. */
    int slotsEndingBy(Instant t) {
        long seconds = Duration.between(horizonStart, t).getSeconds();
        if (seconds <= 0) return 0;
        return (int) Math.min(slotCount, seconds / (slotMinutes * 60L));
    }
}
