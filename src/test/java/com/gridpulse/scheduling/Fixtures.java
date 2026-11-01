package com.gridpulse.scheduling;

import com.gridpulse.shared.domain.ConnectorType;
import com.gridpulse.shared.domain.EnergyKwh;
import com.gridpulse.shared.domain.PowerKw;
import com.gridpulse.shared.domain.StateOfCharge;
import com.gridpulse.shared.domain.TimeWindow;

import java.time.Instant;
import java.util.List;

final class Fixtures {
    static final Instant T0 = Instant.parse("2026-01-05T00:00:00Z");

    private Fixtures() {
    }

    static Instant at(int minutes) {
        return T0.plusSeconds(minutes * 60L);
    }

    static ChargingRequest request(String id, ConnectorType ct, double batteryKwh, int soc, int target,
                                   double rateKw, int arriveMin, int departMin, int priority) {
        return new ChargingRequest(id, ct, EnergyKwh.of(batteryKwh), new StateOfCharge(soc), new StateOfCharge(target),
                PowerKw.of(rateKw), at(arriveMin), at(departMin), priority);
    }

    static ChargerSpec charger(String id, ConnectorType ct, double kw) {
        return new ChargerSpec(id, "g", ct, PowerKw.of(kw), true);
    }

    static SchedulingProblem problem(List<ChargingRequest> requests, List<ChargerSpec> chargers, double gridKw) {
        return new SchedulingProblem(requests, chargers, List.of(new GridLimit("g", PowerKw.of(gridKw))), T0, 15, 8);
    }

    static ScheduledSession session(String vehicle, String charger, int fromMin, int toMin, double kw) {
        return new ScheduledSession(vehicle, charger, new TimeWindow(at(fromMin), at(toMin)), PowerKw.of(kw));
    }
}
