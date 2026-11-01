package com.gridpulse.tariff;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Objects;

/** A time-of-use band in the grid's local time, [start, end); wraps midnight when end is not after start. */
public record TariffBand(LocalTime start, LocalTime end, BigDecimal pricePerKwh) {
    public TariffBand {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(pricePerKwh, "pricePerKwh");
    }

    int startMinute() {
        return start.getHour() * 60 + start.getMinute();
    }

    int endMinute() {
        return end.getHour() * 60 + end.getMinute();
    }
}
