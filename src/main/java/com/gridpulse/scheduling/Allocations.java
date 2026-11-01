package com.gridpulse.scheduling;

import com.gridpulse.shared.domain.TimeWindow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Helpers shared by the schedulers. */
final class Allocations {
    private Allocations() {
    }

    record SlotAllocation(int slot, long watts) {
    }

    /** Available chargers with the vehicle's connector, in a stable order. */
    static List<ChargerSpec> compatibleChargers(SchedulingProblem p, ChargingRequest r) {
        List<ChargerSpec> result = new ArrayList<>();
        for (ChargerSpec c : p.chargers()) {
            if (c.available() && c.connector() == r.connector()) {
                result.add(c);
            }
        }
        result.sort(Comparator.comparing(ChargerSpec::chargerId));
        return result;
    }

    /** Smallest constant power (W) that delivers the remaining energy within one slot. */
    static long wattsToFinish(long remainingWattMinutes, int slotMinutes) {
        return (remainingWattMinutes + slotMinutes - 1) / slotMinutes;
    }

    /** Merges consecutive slots at equal power into sessions. */
    static List<ScheduledSession> toSessions(SchedulingProblem p, String vehicleId, String chargerId,
                                             List<SlotAllocation> allocations) {
        List<SlotAllocation> sorted = new ArrayList<>(allocations);
        sorted.sort(Comparator.comparingInt(SlotAllocation::slot));
        List<ScheduledSession> sessions = new ArrayList<>();
        int i = 0;
        while (i < sorted.size()) {
            int start = sorted.get(i).slot();
            long watts = sorted.get(i).watts();
            int end = start + 1;
            int j = i + 1;
            while (j < sorted.size() && sorted.get(j).slot() == end && sorted.get(j).watts() == watts) {
                end++;
                j++;
            }
            sessions.add(new ScheduledSession(vehicleId, chargerId,
                    new TimeWindow(p.slotStart(start), p.slotStart(end)), Units.kw(watts)));
            i = j;
        }
        return sessions;
    }
}
