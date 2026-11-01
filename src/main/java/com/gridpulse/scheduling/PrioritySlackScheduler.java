package com.gridpulse.scheduling;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Optimized scheduler.
 * <ol>
 *   <li>A {@link PriorityQueue} orders vehicles by business priority (high first), then by
 *       <b>slack</b> = minutes available − minutes needed at full rate (least slack first, like
 *       Earliest-Deadline-First), then departure, then id for determinism.</li>
 *   <li>For each vehicle, every compatible charger is evaluated. Unlike the baseline it may charge in
 *       non-contiguous slots and at <b>reduced power</b> to fit under the remaining grid headroom.</li>
 *   <li>The charger that completes the target earliest wins; if none completes, the one delivering most energy.</li>
 * </ol>
 * Complexity: O(V log V + V · C · S) for V vehicles, C compatible chargers, S slots.
 */
public final class PrioritySlackScheduler implements ChargingScheduler {
    public static final String NAME = "priority-slack";

    /** Slots with less headroom than this are skipped (except to finish a tiny remainder). */
    static final long MIN_SESSION_WATTS = 1_000;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ChargingSchedule schedule(SchedulingProblem p) {
        CapacityLedger ledger = new CapacityLedger(p);
        List<ScheduledSession> sessions = new ArrayList<>();
        List<UnmetRequest> unmet = new ArrayList<>();

        PriorityQueue<ChargingRequest> queue = new PriorityQueue<>(
                Comparator.comparingInt(ChargingRequest::priority).reversed()
                        .thenComparingLong(PrioritySlackScheduler::slackMinutes)
                        .thenComparing(ChargingRequest::departure)
                        .thenComparing(ChargingRequest::vehicleId));
        queue.addAll(p.requests());

        while (!queue.isEmpty()) {
            ChargingRequest r = queue.poll();
            long need = Units.wattMinutes(r.energyRequired());
            if (need == 0) {
                continue;
            }
            List<ChargerSpec> compatible = Allocations.compatibleChargers(p, r);
            if (compatible.isEmpty()) {
                unmet.add(UnmetRequest.of(r, UnmetReason.NO_COMPATIBLE_CHARGER, 0));
                continue;
            }
            int first = p.firstSlotAtOrAfter(r.arrival());
            int end = p.slotsEndingBy(r.departure());

            Plan best = null;
            for (ChargerSpec c : compatible) {
                Plan plan = plan(p, ledger, r, c, need, first, end);
                if (best == null || plan.isBetterThan(best)) {
                    best = plan;
                }
            }
            if (best.delivered() == 0) {
                unmet.add(UnmetRequest.of(r, UnmetReason.NO_CAPACITY_BEFORE_DEPARTURE, 0));
                continue;
            }
            for (Allocations.SlotAllocation a : best.allocations()) {
                ledger.allocate(best.charger(), r.vehicleId(), a.slot(), a.watts());
            }
            sessions.addAll(Allocations.toSessions(p, r.vehicleId(), best.charger().chargerId(), best.allocations()));
            if (!best.complete()) {
                unmet.add(UnmetRequest.of(r, UnmetReason.DEADLINE_MISSED, best.delivered()));
            }
        }
        return new ChargingSchedule(NAME, sessions, unmet);
    }

    static long slackMinutes(ChargingRequest r) {
        long windowMinutes = Duration.between(r.arrival(), r.departure()).toMinutes();
        long needWattMinutes = Units.wattMinutes(r.energyRequired());
        long rateWatts = Units.watts(r.maxChargeRate());
        long minimumMinutes = (needWattMinutes + rateWatts - 1) / rateWatts;
        return windowMinutes - minimumMinutes;
    }

    private static Plan plan(SchedulingProblem p, CapacityLedger ledger, ChargingRequest r, ChargerSpec c,
                             long need, int first, int end) {
        long chargerWatts = Math.min(Units.watts(c.maxPower()), Units.watts(r.maxChargeRate()));
        List<Allocations.SlotAllocation> allocations = new ArrayList<>();
        long delivered = 0;
        int finishSlot = -1;
        for (int s = first; s < end && delivered < need; s++) {
            if (!ledger.chargerFree(c.chargerId(), s)) {
                continue;
            }
            long toFinish = Allocations.wattsToFinish(need - delivered, p.slotMinutes());
            long w = Math.min(chargerWatts, Math.min(ledger.headroomWatts(c.gridId(), s), toFinish));
            if (w <= 0 || (w < MIN_SESSION_WATTS && w < toFinish)) {
                continue;
            }
            allocations.add(new Allocations.SlotAllocation(s, w));
            delivered += w * p.slotMinutes();
            finishSlot = s;
        }
        return new Plan(c, allocations, delivered, delivered >= need, finishSlot);
    }

    private record Plan(ChargerSpec charger, List<Allocations.SlotAllocation> allocations,
                        long delivered, boolean complete, int finishSlot) {
        boolean isBetterThan(Plan other) {
            if (complete != other.complete) {
                return complete;
            }
            if (complete && finishSlot != other.finishSlot) {
                return finishSlot < other.finishSlot;
            }
            if (!complete && delivered != other.delivered) {
                return delivered > other.delivered;
            }
            return charger.chargerId().compareTo(other.charger.chargerId()) < 0;
        }
    }
}
