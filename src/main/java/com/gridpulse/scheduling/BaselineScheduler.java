package com.gridpulse.scheduling;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Baseline for comparison: first-come, first-served at full power.
 * <ol>
 *   <li>Vehicles are handled in arrival order.</li>
 *   <li>Each takes the earliest slot where a compatible charger is free AND the grid can supply full power.</li>
 *   <li>It charges contiguously at full power until done, departure, or the grid/charger blocks it.</li>
 * </ol>
 * It never violates hard constraints, but it ignores deadlines and priorities.
 */
public final class BaselineScheduler implements ChargingScheduler {
    public static final String NAME = "baseline-fcfs";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ChargingSchedule schedule(SchedulingProblem p) {
        CapacityLedger ledger = new CapacityLedger(p);
        List<ScheduledSession> sessions = new ArrayList<>();
        List<UnmetRequest> unmet = new ArrayList<>();

        List<ChargingRequest> order = new ArrayList<>(p.requests());
        order.sort(Comparator.comparing(ChargingRequest::arrival).thenComparing(ChargingRequest::vehicleId));

        for (ChargingRequest r : order) {
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
            long vehicleWatts = Units.watts(r.maxChargeRate());

            ChargerSpec chosen = null;
            int startSlot = -1;
            long fullWatts = 0;
            search:
            for (int s = first; s < end; s++) {
                for (ChargerSpec c : compatible) {
                    long w = Math.min(Units.watts(c.maxPower()), vehicleWatts);
                    if (ledger.chargerFree(c.chargerId(), s) && ledger.vehicleFree(r.vehicleId(), s)
                            && ledger.headroomWatts(c.gridId(), s) >= w) {
                        chosen = c;
                        startSlot = s;
                        fullWatts = w;
                        break search;
                    }
                }
            }
            if (chosen == null) {
                unmet.add(UnmetRequest.of(r, UnmetReason.NO_CAPACITY_BEFORE_DEPARTURE, 0));
                continue;
            }

            List<Allocations.SlotAllocation> allocations = new ArrayList<>();
            long delivered = 0;
            for (int s = startSlot; s < end && delivered < need; s++) {
                long w = Math.min(fullWatts, Allocations.wattsToFinish(need - delivered, p.slotMinutes()));
                if (!ledger.chargerFree(chosen.chargerId(), s) || !ledger.vehicleFree(r.vehicleId(), s)
                        || ledger.headroomWatts(chosen.gridId(), s) < w) {
                    break;
                }
                ledger.allocate(chosen, r.vehicleId(), s, w);
                allocations.add(new Allocations.SlotAllocation(s, w));
                delivered += w * p.slotMinutes();
            }
            sessions.addAll(Allocations.toSessions(p, r.vehicleId(), chosen.chargerId(), allocations));
            if (delivered < need) {
                unmet.add(UnmetRequest.of(r, UnmetReason.DEADLINE_MISSED, delivered));
            }
        }
        return new ChargingSchedule(NAME, sessions, unmet);
    }
}
