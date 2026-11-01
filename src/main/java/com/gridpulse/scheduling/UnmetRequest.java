package com.gridpulse.scheduling;

import com.gridpulse.shared.domain.EnergyKwh;

/** A vehicle whose target could not be met, with the reason. Never silently dropped. */
public record UnmetRequest(String vehicleId, UnmetReason reason, EnergyKwh required, EnergyKwh delivered) {
    static UnmetRequest of(ChargingRequest r, UnmetReason reason, long deliveredWattMinutes) {
        return new UnmetRequest(r.vehicleId(), reason, r.energyRequired(), Units.kwh(deliveredWattMinutes));
    }
}
