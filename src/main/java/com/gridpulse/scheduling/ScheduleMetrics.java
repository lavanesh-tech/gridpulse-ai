package com.gridpulse.scheduling;

import com.gridpulse.shared.domain.EnergyKwh;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/** Outcome measures for one schedule. Computed from the schedule itself, never hardcoded. */
public record ScheduleMetrics(
        String algorithm,
        int vehicles,
        int vehiclesNeedingCharge,
        int sessions,
        int deadlinesMet,
        double deadlineSuccessRate,
        int violations,
        double chargerUtilization,
        EnergyKwh energyDelivered,
        EnergyKwh energyRequired,
        List<PriorityOutcome> byPriority,
        ScheduleCost cost) {

    public static ScheduleMetrics of(SchedulingProblem p, ChargingSchedule s, List<Violation> violations) {
        Set<String> unmetIds = new HashSet<>();
        s.unmet().forEach(u -> unmetIds.add(u.vehicleId()));
        int needing = 0;
        int met = 0;
        EnergyKwh required = EnergyKwh.ZERO;
        TreeMap<Integer, int[]> perPriority = new TreeMap<>(Comparator.reverseOrder());
        for (ChargingRequest r : p.requests()) {
            EnergyKwh need = r.energyRequired();
            if (need.compareTo(EnergyKwh.ZERO) > 0) {
                needing++;
                required = required.plus(need);
                int[] counts = perPriority.computeIfAbsent(r.priority(), k -> new int[2]);
                counts[0]++;
                if (!unmetIds.contains(r.vehicleId())) {
                    met++;
                    counts[1]++;
                }
            }
        }
        List<PriorityOutcome> byPriority = new ArrayList<>();
        perPriority.forEach((prio, c) -> byPriority.add(new PriorityOutcome(prio, c[0], c[1], (double) c[1] / c[0])));

        long occupiedMinutes = 0;
        EnergyKwh delivered = EnergyKwh.ZERO;
        for (ScheduledSession session : s.sessions()) {
            occupiedMinutes += session.window().minutes();
            delivered = delivered.plus(session.power().over(session.window().minutes()));
        }
        long availableChargers = p.chargers().stream().filter(ChargerSpec::available).count();
        long capacityMinutes = availableChargers * (long) p.slotCount() * p.slotMinutes();

        return new ScheduleMetrics(
                s.algorithm(),
                p.requests().size(),
                needing,
                s.sessions().size(),
                met,
                needing == 0 ? 1.0 : (double) met / needing,
                violations.size(),
                capacityMinutes == 0 ? 0.0 : (double) occupiedMinutes / capacityMinutes,
                delivered,
                required,
                byPriority,
                ScheduleCost.of(p, s));
    }
}
