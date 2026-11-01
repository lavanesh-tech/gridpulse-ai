package com.gridpulse.shared.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Electrical power in kilowatts. Never negative. */
public record PowerKw(BigDecimal value) implements Comparable<PowerKw> {
    public static final PowerKw ZERO = new PowerKw(BigDecimal.ZERO);

    public PowerKw {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException("power must be >= 0 kW, was " + value);
        }
        value = value.setScale(3, RoundingMode.HALF_UP);
    }

    public static PowerKw of(double kw) { return new PowerKw(BigDecimal.valueOf(kw)); }

    public PowerKw plus(PowerKw other) { return new PowerKw(value.add(other.value)); }

    public boolean exceeds(PowerKw limit) { return compareTo(limit) > 0; }

    public PowerKw min(PowerKw other) { return compareTo(other) <= 0 ? this : other; }

    /** Energy delivered when this power is applied for the given number of minutes. */
    public EnergyKwh over(long minutes) {
        return new EnergyKwh(value.multiply(BigDecimal.valueOf(minutes))
                .divide(BigDecimal.valueOf(60), 3, RoundingMode.HALF_UP));
    }

    @Override public int compareTo(PowerKw o) { return value.compareTo(o.value); }
}
