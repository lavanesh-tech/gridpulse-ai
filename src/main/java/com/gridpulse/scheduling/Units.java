package com.gridpulse.scheduling;

import com.gridpulse.shared.domain.EnergyKwh;
import com.gridpulse.shared.domain.PowerKw;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Internal integer units so the engine does exact arithmetic quickly:
 * power in watts, energy in watt-minutes (1 kWh = 60,000 Wmin).
 */
final class Units {
    private Units() {
    }

    static long watts(PowerKw p) {
        return p.value().movePointRight(3).longValueExact();
    }

    static PowerKw kw(long watts) {
        return new PowerKw(BigDecimal.valueOf(watts, 3));
    }

    static long wattMinutes(EnergyKwh e) {
        return e.value().movePointRight(3).longValueExact() * 60;
    }

    static EnergyKwh kwh(long wattMinutes) {
        return new EnergyKwh(BigDecimal.valueOf(wattMinutes).divide(BigDecimal.valueOf(60_000), 3, RoundingMode.HALF_UP));
    }
}
