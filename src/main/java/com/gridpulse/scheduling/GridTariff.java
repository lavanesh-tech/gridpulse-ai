package com.gridpulse.scheduling;

import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

/** Time-of-use tariff for one grid connection. Times not covered by any band are priced at zero. */
public record GridTariff(String gridId, ZoneId zone, List<TariffRate> rates) {
    public GridTariff {
        Objects.requireNonNull(gridId, "gridId");
        Objects.requireNonNull(zone, "zone");
        rates = List.copyOf(rates);
        boolean[] minute = new boolean[24 * 60];
        for (TariffRate r : rates) {
            for (int m = 0; m < minute.length; m++) {
                if (r.covers(java.time.LocalTime.of(m / 60, m % 60))) {
                    if (minute[m]) throw new IllegalArgumentException("overlapping tariff bands for grid " + gridId);
                    minute[m] = true;
                }
            }
        }
    }
}
