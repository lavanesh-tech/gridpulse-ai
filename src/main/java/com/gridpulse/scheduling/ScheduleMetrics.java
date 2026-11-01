package com.gridpulse.scheduling;

import com.gridpulse.shared.domain.EnergyKwh;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        EnergyKwh energyRequired) {

    public static ScheduleMetrics of(SchedulingProblem p, ChargingSchedule s, List<Violation> violations) {
        int needing = 0;
        EnergyKwh required = EnergyKwh.ZERO;
        for (ChargingRequest r : p.requests()) {
            EnergyKwh need = r.energyRequired();
            if (need.compareTo(EnergyKwh.ZERO) > 0) {
                needing++;
                required = required.plus(need);
            }
        }
        Set<String> unmetIds = new HashSet<>();
        s.unmet().forEach(u -> unmetIds.add(u.vehicleId()));
        int met = needing - unmetIds.size();

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
                required);
    }
}
