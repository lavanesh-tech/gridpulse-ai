package com.gridpulse.shared.domain;

/** Battery state of charge as a whole percentage, 0..100 inclusive. */
public record StateOfCharge(int percent) {
    public StateOfCharge {
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("state of charge must be 0..100, was " + percent);
        }
    }

    /** Energy needed to go from this SoC to the target, for a battery of the given capacity. */
    public EnergyKwh energyNeededTo(StateOfCharge target, EnergyKwh batteryCapacity) {
        if (target.percent <= percent) {
            return EnergyKwh.ZERO;
        }
        double kwh = batteryCapacity.value().doubleValue() * (target.percent - percent) / 100.0;
        return EnergyKwh.of(kwh);
    }
}
