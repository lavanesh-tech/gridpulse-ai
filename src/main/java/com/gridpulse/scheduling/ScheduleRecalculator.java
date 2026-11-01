package com.gridpulse.scheduling;

import com.gridpulse.shared.domain.StateOfCharge;
import com.gridpulse.shared.domain.TimeWindow;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Re-plans the remainder of a schedule after a {@link Disruption}.
 * <ol>
 *   <li>Sessions that finished before the disruption are kept as history. A session running on a charger that
 *       failed is cut at the disruption instant; other running sessions continue to the next slot boundary.</li>
 *   <li>Energy already delivered is credited to each vehicle (rounded down to whole SoC percent, so the
 *       remaining need is never under-estimated).</li>
 *   <li>The remaining needs are scheduled again from the next slot boundary with failed chargers removed and
 *       curtailed grid limits applied, using the same deterministic scheduler.</li>
 *   <li>The combined result is validated: kept + new against the original constraints, and new sessions against
 *       the post-disruption constraints.</li>
 * </ol>
 */
public final class ScheduleRecalculator {
    private final ChargingScheduler scheduler;
    private final ScheduleValidator validator = new ScheduleValidator();

    public ScheduleRecalculator(ChargingScheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    public RecalculationResult recalculate(SchedulingProblem p, ChargingSchedule previous, Disruption d) {
        int fromSlot = p.firstSlotAtOrAfter(d.at());
        Instant replanFrom = p.slotStart(fromSlot);

        List<ScheduledSession> kept = new ArrayList<>();
        Map<String, Long> deliveredWattMinutes = new HashMap<>();
        int interrupted = 0;
        int replanned = 0;
        for (ScheduledSession s : previous.sessions()) {
            Instant cut = d.chargersOutOfService().contains(s.chargerId()) ? d.at() : replanFrom;
            if (!s.window().start().isBefore(cut)) {
                replanned++;
                continue;
            }
            ScheduledSession keptPart = s;
            if (s.window().end().isAfter(cut)) {
                keptPart = new ScheduledSession(s.vehicleId(), s.chargerId(), new TimeWindow(s.window().start(), cut), s.power());
                if (cut.equals(d.at()) && d.chargersOutOfService().contains(s.chargerId())) interrupted++;
                else replanned++;
            }
            kept.add(keptPart);
            long seconds = Duration.between(keptPart.window().start(), keptPart.window().end()).getSeconds();
            deliveredWattMinutes.merge(s.vehicleId(), Units.watts(s.power()) * seconds / 60, Long::sum);
        }

        List<ChargingRequest> remaining = new ArrayList<>();
        List<UnmetRequest> outOfTime = new ArrayList<>();
        for (ChargingRequest r : p.requests()) {
            long need = Units.wattMinutes(r.energyRequired());
            long delivered = deliveredWattMinutes.getOrDefault(r.vehicleId(), 0L);
            if (need == 0 || delivered >= need) continue;
            long batteryWattMinutes = Units.wattMinutes(r.batteryCapacity());
            int gainedPct = (int) (delivered * 100 / batteryWattMinutes);
            StateOfCharge nowSoc = new StateOfCharge(Math.min(r.targetSoc().percent(), r.currentSoc().percent() + gainedPct));
            Instant arrival = r.arrival().isAfter(replanFrom) ? r.arrival() : replanFrom;
            if (!r.departure().isAfter(arrival)) {
                outOfTime.add(UnmetRequest.of(r, UnmetReason.DEADLINE_MISSED, delivered));
                continue;
            }
            remaining.add(new ChargingRequest(r.vehicleId(), r.connector(), r.batteryCapacity(), nowSoc, r.targetSoc(),
                    r.maxChargeRate(), arrival, r.departure(), r.priority()));
        }

        List<ChargerSpec> chargers = new ArrayList<>();
        for (ChargerSpec c : p.chargers()) {
            boolean down = d.chargersOutOfService().contains(c.chargerId());
            chargers.add(down ? new ChargerSpec(c.chargerId(), c.gridId(), c.connector(), c.maxPower(), false) : c);
        }
        List<GridLimit> grids = new ArrayList<>();
        for (GridLimit g : p.grids()) {
            grids.add(d.gridCapacityOverrides().containsKey(g.gridId())
                    ? new GridLimit(g.gridId(), d.gridCapacityOverrides().get(g.gridId())) : g);
        }
        SchedulingProblem after = p.with(remaining, chargers, grids);
        ChargingSchedule future = scheduler.schedule(after);

        List<ScheduledSession> all = new ArrayList<>(kept);
        all.addAll(future.sessions());
        List<UnmetRequest> unmet = new ArrayList<>(outOfTime);
        for (UnmetRequest u : future.unmet()) {
            // report against the original need so totals stay comparable
            ChargingRequest original = p.requests().stream().filter(r -> r.vehicleId().equals(u.vehicleId())).findFirst().orElseThrow();
            long delivered = deliveredWattMinutes.getOrDefault(u.vehicleId(), 0L) + Units.wattMinutes(u.delivered());
            unmet.add(UnmetRequest.of(original, u.reason(), delivered));
        }
        ChargingSchedule combined = new ChargingSchedule(scheduler.name() + "+recalculated", all, unmet);

        List<Violation> violations = new ArrayList<>(validator.validate(p, combined));
        violations.addAll(validator.validate(after, new ChargingSchedule(scheduler.name(), future.sessions(), List.of())));

        Set<String> before = new HashSet<>();
        previous.unmet().forEach(u -> before.add(u.vehicleId()));
        List<String> newlyUnmet = unmet.stream().map(UnmetRequest::vehicleId).filter(v -> !before.contains(v)).sorted().toList();
        return new RecalculationResult(combined, replanFrom, kept.size(), interrupted, replanned, newlyUnmet, violations);
    }
}
