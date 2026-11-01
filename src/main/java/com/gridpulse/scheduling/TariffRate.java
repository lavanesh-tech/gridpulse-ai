package com.gridpulse.scheduling;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Objects;

/**
 * One time-of-use price band in local time, [start, end). If {@code end} is not after {@code start}
 * the band wraps past midnight (e.g. 21:00–06:00).
 */
public record TariffRate(LocalTime start, LocalTime end, BigDecimal pricePerKwh) {
    public TariffRate {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(pricePerKwh, "pricePerKwh");
        if (start.equals(end)) throw new IllegalArgumentException("tariff band must not be empty");
        if (pricePerKwh.signum() < 0) throw new IllegalArgumentException("price must be >= 0");
        if (pricePerKwh.scale() > 6) throw new IllegalArgumentException("price precision is limited to 6 decimals");
    }

    boolean covers(LocalTime t) {
        return start.isBefore(end)
                ? !t.isBefore(start) && t.isBefore(end)
                : !t.isBefore(start) || t.isBefore(end);
    }
}
