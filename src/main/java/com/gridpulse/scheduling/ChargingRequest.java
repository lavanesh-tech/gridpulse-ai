package com.gridpulse.scheduling;

import com.gridpulse.shared.domain.ConnectorType;
import com.gridpulse.shared.domain.EnergyKwh;
import com.gridpulse.shared.domain.PowerKw;
import com.gridpulse.shared.domain.StateOfCharge;

import java.time.Instant;
import java.util.Objects;

/** One vehicle's charging need: what it is, how full it is, and when it is plugged in. */
public record ChargingRequest(
        String vehicleId,
        ConnectorType connector,
        EnergyKwh batteryCapacity,
        StateOfCharge currentSoc,
        StateOfCharge targetSoc,
        PowerKw maxChargeRate,
        Instant arrival,
        Instant departure,
        int priority) {

    public ChargingRequest {
        Objects.requireNonNull(vehicleId, "vehicleId");
        Objects.requireNonNull(connector, "connector");
        Objects.requireNonNull(batteryCapacity, "batteryCapacity");
        Objects.requireNonNull(currentSoc, "currentSoc");
        Objects.requireNonNull(targetSoc, "targetSoc");
        Objects.requireNonNull(maxChargeRate, "maxChargeRate");
        Objects.requireNonNull(arrival, "arrival");
        Objects.requireNonNull(departure, "departure");
        if (!departure.isAfter(arrival)) {
            throw new IllegalArgumentException("departure must be after arrival for " + vehicleId);
        }
        if (priority < 0) {
            throw new IllegalArgumentException("priority must be >= 0");
        }
        if (maxChargeRate.compareTo(PowerKw.ZERO) <= 0) {
            throw new IllegalArgumentException("maxChargeRate must be > 0");
        }
    }

    public EnergyKwh energyRequired() {
        return currentSoc.energyNeededTo(targetSoc, batteryCapacity);
    }
}
