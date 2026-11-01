package com.gridpulse.scheduling;

import com.gridpulse.shared.domain.ConnectorType;
import com.gridpulse.shared.domain.PowerKw;

import java.util.Objects;

/** A charger as the scheduler sees it. {@code available=false} models an outage. */
public record ChargerSpec(String chargerId, String gridId, ConnectorType connector, PowerKw maxPower, boolean available) {
    public ChargerSpec {
        Objects.requireNonNull(chargerId, "chargerId");
        Objects.requireNonNull(gridId, "gridId");
        Objects.requireNonNull(connector, "connector");
        Objects.requireNonNull(maxPower, "maxPower");
    }
}
