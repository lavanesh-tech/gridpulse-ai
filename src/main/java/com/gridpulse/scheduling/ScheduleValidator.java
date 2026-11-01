package com.gridpulse.scheduling;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Independently checks any schedule (from any scheduler, the AI advisor, or a human edit) against
 * every hard constraint. It does not trust the scheduler that produced the schedule.
 */
public final class ScheduleValidator {

    private record GridEvent(Instant at, long deltaWatts) {
    }

    public List<Violation> validate(SchedulingProblem p, ChargingSchedule schedule) {
        Map<String, ChargingRequest> requests = new HashMap<>();
        p.requests().forEach(r -> requests.put(r.vehicleId(), r));
        Map<String, ChargerSpec> chargers = new HashMap<>();
        p.chargers().forEach(c -> chargers.put(c.chargerId(), c));
        Map<String, Long> gridCapacity = new HashMap<>();
        p.grids().forEach(g -> gridCapacity.put(g.gridId(), Units.watts(g.capacity())));

        List<Violation> violations = new ArrayList<>();
        Map<String, List<ScheduledSession>> byCharger = new HashMap<>();
        Map<String, List<ScheduledSession>> byVehicle = new HashMap<>();
        Map<String, List<GridEvent>> gridEvents = new HashMap<>();

        for (ScheduledSession s : schedule.sessions()) {
            ChargingRequest r = requests.get(s.vehicleId());
            ChargerSpec c = chargers.get(s.chargerId());
            if (r == null) {
                violations.add(new Violation(ViolationType.UNKNOWN_VEHICLE, s.vehicleId()));
                continue;
            }
            if (c == null) {
                violations.add(new Violation(ViolationType.UNKNOWN_CHARGER, s.chargerId()));
                continue;
            }
            String where = s.vehicleId() + " on " + s.chargerId() + " " + s.window();
            if (!c.available()) {
                violations.add(new Violation(ViolationType.CHARGER_UNAVAILABLE, where));
            }
            if (c.connector() != r.connector()) {
                violations.add(new Violation(ViolationType.INCOMPATIBLE_CONNECTOR,
                        where + ": " + r.connector() + " vs " + c.connector()));
            }
            if (s.power().exceeds(c.maxPower())) {
                violations.add(new Violation(ViolationType.POWER_EXCEEDS_CHARGER, where));
            }
            if (s.power().exceeds(r.maxChargeRate())) {
                violations.add(new Violation(ViolationType.POWER_EXCEEDS_VEHICLE, where));
            }
            if (s.window().start().isBefore(r.arrival()) || s.window().end().isAfter(r.departure())) {
                violations.add(new Violation(ViolationType.OUTSIDE_VEHICLE_WINDOW, where));
            }
            byCharger.computeIfAbsent(s.chargerId(), k -> new ArrayList<>()).add(s);
            byVehicle.computeIfAbsent(s.vehicleId(), k -> new ArrayList<>()).add(s);
            long w = Units.watts(s.power());
            List<GridEvent> events = gridEvents.computeIfAbsent(c.gridId(), k -> new ArrayList<>());
            events.add(new GridEvent(s.window().start(), w));
            events.add(new GridEvent(s.window().end(), -w));
        }

        // Capacity already committed outside this schedule counts towards overlaps and grid load.
        for (FixedBooking b : p.fixedBookings()) {
            ChargerSpec c = chargers.get(b.chargerId());
            if (c == null) continue;
            ScheduledSession existing = new ScheduledSession(
                    b.vehicleId() == null ? "<existing booking>" : b.vehicleId(), b.chargerId(), b.window(), b.power());
            byCharger.computeIfAbsent(b.chargerId(), k -> new ArrayList<>()).add(existing);
            if (b.vehicleId() != null) {
                byVehicle.computeIfAbsent(b.vehicleId(), k -> new ArrayList<>()).add(existing);
            }
            long w = Units.watts(b.power());
            List<GridEvent> events = gridEvents.computeIfAbsent(c.gridId(), k -> new ArrayList<>());
            events.add(new GridEvent(b.window().start(), w));
            events.add(new GridEvent(b.window().end(), -w));
        }

        checkOverlaps(byCharger, ViolationType.CHARGER_DOUBLE_BOOKED, violations);
        checkOverlaps(byVehicle, ViolationType.VEHICLE_ON_MULTIPLE_CHARGERS, violations);
        checkGridCapacity(gridEvents, gridCapacity, violations);
        return violations;
    }

    /** Sort by start; any session starting before the latest end so far overlaps. O(n log n). */
    private static void checkOverlaps(Map<String, List<ScheduledSession>> groups, ViolationType type,
                                      List<Violation> out) {
        for (Map.Entry<String, List<ScheduledSession>> e : groups.entrySet()) {
            List<ScheduledSession> list = new ArrayList<>(e.getValue());
            list.sort(Comparator.comparing((ScheduledSession s) -> s.window().start()));
            Instant latestEnd = null;
            for (ScheduledSession s : list) {
                if (latestEnd != null && s.window().start().isBefore(latestEnd)) {
                    out.add(new Violation(type, e.getKey() + " at " + s.window()));
                }
                if (latestEnd == null || s.window().end().isAfter(latestEnd)) {
                    latestEnd = s.window().end();
                }
            }
        }
    }

    /** Sweep line: apply all start/end events at each instant, then compare the running load to capacity. */
    private static void checkGridCapacity(Map<String, List<GridEvent>> gridEvents, Map<String, Long> capacity,
                                          List<Violation> out) {
        for (Map.Entry<String, List<GridEvent>> e : gridEvents.entrySet()) {
            List<GridEvent> events = new ArrayList<>(e.getValue());
            events.sort(Comparator.comparing(GridEvent::at));
            long cap = capacity.get(e.getKey());
            long load = 0;
            int i = 0;
            while (i < events.size()) {
                Instant t = events.get(i).at();
                while (i < events.size() && events.get(i).at().equals(t)) {
                    load += events.get(i).deltaWatts();
                    i++;
                }
                if (load > cap) {
                    out.add(new Violation(ViolationType.GRID_CAPACITY_EXCEEDED,
                            e.getKey() + " at " + t + ": " + load + " W > " + cap + " W"));
                }
            }
        }
    }
}
