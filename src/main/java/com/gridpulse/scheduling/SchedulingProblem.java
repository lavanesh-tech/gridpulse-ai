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
 * {@code tariffs} prices energy per grid (optional); {@code fixedBookings} is capacity already committed.
 */
public record SchedulingProblem(
        List<ChargingRequest> requests,
        List<ChargerSpec> chargers,
        List<GridLimit> grids,
        Instant horizonStart,
        int slotMinutes,
        int slotCount,
        List<GridTariff> tariffs,
        List<FixedBooking> fixedBookings) {

    /** Problem without tariffs or existing bookings. */
    public SchedulingProblem(List<ChargingRequest> requests, List<ChargerSpec> chargers, List<GridLimit> grids,
                             Instant horizonStart, int slotMinutes, int slotCount) {
        this(requests, chargers, grids, horizonStart, slotMinutes, slotCount, List.of(), List.of());
    }

    public SchedulingProblem {
        requests = List.copyOf(requests);
        chargers = List.copyOf(chargers);
        grids = List.copyOf(grids);
        tariffs = List.copyOf(tariffs);
        fixedBookings = List.copyOf(fixedBookings);
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
        Set<String> tariffGrids = new HashSet<>();
        for (GridTariff t : tariffs) {
            if (!gridIds.contains(t.gridId())) throw new IllegalArgumentException("tariff for unknown grid " + t.gridId());
            if (!tariffGrids.add(t.gridId())) throw new IllegalArgumentException("duplicate tariff for grid " + t.gridId());
        }
        for (FixedBooking b : fixedBookings) {
            if (!chargerIds.contains(b.chargerId())) throw new IllegalArgumentException("booking on unknown charger " + b.chargerId());
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

    /** Same problem with different requests/chargers/grids, keeping horizon, tariffs and bookings. */
    public SchedulingProblem with(List<ChargingRequest> newRequests, List<ChargerSpec> newChargers, List<GridLimit> newGrids) {
        return new SchedulingProblem(newRequests, newChargers, newGrids, horizonStart, slotMinutes, slotCount,
                tariffs, fixedBookings);
    }

    /** Index of the slot containing {@code t}, clamped to [0, slotCount). */
    int slotContaining(Instant t) {
        long seconds = Duration.between(horizonStart, t).getSeconds();
        if (seconds <= 0) return 0;
        return (int) Math.min(slotCount - 1, seconds / (slotMinutes * 60L));
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
