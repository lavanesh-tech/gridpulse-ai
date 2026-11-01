package com.gridpulse.shared.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Electrical energy in kilowatt-hours. Never negative. */
public record EnergyKwh(BigDecimal value) implements Comparable<EnergyKwh> {
    public static final EnergyKwh ZERO = new EnergyKwh(BigDecimal.ZERO);

    public EnergyKwh {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException("energy must be >= 0 kWh, was " + value);
        }
        value = value.setScale(3, RoundingMode.HALF_UP);
    }

    public static EnergyKwh of(double kwh) { return new EnergyKwh(BigDecimal.valueOf(kwh)); }

    public EnergyKwh plus(EnergyKwh o) { return new EnergyKwh(value.add(o.value)); }

    /** Subtraction floored at zero (you cannot owe negative energy). */
    public EnergyKwh minusFloorZero(EnergyKwh o) {
        BigDecimal r = value.subtract(o.value);
        return r.signum() < 0 ? ZERO : new EnergyKwh(r);
    }

    @Override public int compareTo(EnergyKwh o) { return value.compareTo(o.value); }
}
