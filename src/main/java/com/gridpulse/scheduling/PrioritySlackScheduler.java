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
 *   <li>For each vehicle and each compatible charger, candidate slots inside the vehicle's window are tried in
 *       an order set by the {@link Objective}. Power may be reduced to fit under the remaining grid headroom;
 *       slots need not be contiguous.</li>
 *   <li>A charger whose plan completes the target wins over one that does not, then the objective decides.</li>
 * </ol>
 * The two objectives genuinely conflict; BENCHMARKS.md reports the measured trade-off.
 * Complexity: O(V log V + V · C · S) for V vehicles, C compatible chargers, S slots.
 */
public final class PrioritySlackScheduler implements ChargingScheduler {
    public static final String NAME = "priority-slack";
    public static final String COST_NAME = "priority-slack-cost";

    public enum Objective {
        /**
         * Latest slots first, ignoring price. Leaves early slots (when most vehicles have just arrived) to vehicles
         * that need them. Among chargers, the earliest finish wins, then the lower cost.
         */
        DEADLINES_FIRST,
        /** Cheapest slots first (ties: latest first). Among chargers, the lower cost wins, then the earliest finish. */
        COST_FIRST
    }

    private final Objective objective;

    public PrioritySlackScheduler() {
        this(Objective.DEADLINES_FIRST);
    }

    public PrioritySlackScheduler(Objective objective) {
        this.objective = java.util.Objects.requireNonNull(objective, "objective");
    }

    /** Slots with less headroom than this are skipped (except to finish a tiny remainder). */
    static final long MIN_SESSION_WATTS = 1_000;

    @Override
    public String name() {
        return objective == Objective.COST_FIRST ? COST_NAME : NAME;
    }

    @Override
    public ChargingSchedule schedule(SchedulingProblem p) {
        CapacityLedger ledger = new CapacityLedger(p);
        PriceTable prices = new PriceTable(p);
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
                Plan plan = plan(p, ledger, prices, r, c, need, first, end);
                if (best == null || plan.isBetterThan(best, objective)) {
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
        return new ChargingSchedule(name(), sessions, unmet);
    }

    static long slackMinutes(ChargingRequest r) {
        long windowMinutes = Duration.between(r.arrival(), r.departure()).toMinutes();
        long needWattMinutes = Units.wattMinutes(r.energyRequired());
        long rateWatts = Units.watts(r.maxChargeRate());
        long minimumMinutes = (needWattMinutes + rateWatts - 1) / rateWatts;
        return windowMinutes - minimumMinutes;
    }

    private Plan plan(SchedulingProblem p, CapacityLedger ledger, PriceTable prices, ChargingRequest r,
                      ChargerSpec c, long need, int first, int end) {
        // Slot order: DEADLINES_FIRST = latest first; COST_FIRST = cheapest first, then latest (precomputed per grid).
        int[] order = objective == Objective.COST_FIRST ? prices.slotsCheapestThenLatest(c.gridId()) : null;
        int total = p.slotCount();

        long chargerWatts = Math.min(Units.watts(c.maxPower()), Units.watts(r.maxChargeRate()));
        List<Allocations.SlotAllocation> allocations = new ArrayList<>();
        long delivered = 0;
        long costMicros = 0;
        int finishSlot = -1;
        for (int i = 0; i < total && delivered < need; i++) {
            int s = order != null ? order[i] : total - 1 - i;
            if (s < first || s >= end || !ledger.chargerFree(c.chargerId(), s) || !ledger.vehicleFree(r.vehicleId(), s)) {
                continue;
            }
            long toFinish = Allocations.wattsToFinish(need - delivered, p.slotMinutes());
            long w = Math.min(chargerWatts, Math.min(ledger.headroomWatts(c.gridId(), s), toFinish));
            if (w <= 0 || (w < MIN_SESSION_WATTS && w < toFinish)) {
                continue;
            }
            allocations.add(new Allocations.SlotAllocation(s, w));
            delivered += w * p.slotMinutes();
            costMicros += PriceTable.costMicros(w, p.slotMinutes(), prices.priceMicros(c.gridId(), s));
            finishSlot = Math.max(finishSlot, s);
        }
        return new Plan(c, allocations, delivered, delivered >= need, costMicros, finishSlot);
    }

    private record Plan(ChargerSpec charger, List<Allocations.SlotAllocation> allocations,
                        long delivered, boolean complete, long costMicros, int finishSlot) {
        boolean isBetterThan(Plan other, Objective objective) {
            if (complete != other.complete) {
                return complete;
            }
            if (!complete && delivered != other.delivered) {
                return delivered > other.delivered;
            }
            if (objective == Objective.COST_FIRST && costMicros != other.costMicros) {
                return costMicros < other.costMicros;
            }
            if (finishSlot != other.finishSlot) {
                return finishSlot < other.finishSlot;
            }
            if (costMicros != other.costMicros) {
                return costMicros < other.costMicros;
            }
            return charger.chargerId().compareTo(other.charger.chargerId()) < 0;
        }
    }
}
