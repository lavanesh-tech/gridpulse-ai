package com.gridpulse.scheduling;

import com.gridpulse.shared.domain.PowerKw;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Something that changed at {@code at}: chargers that went out of service (station outage or charger fault,
 * which also interrupts any session running on them) and/or grid connections whose capacity was reduced
 * (utility curtailment). Applies from {@code at} onwards; what happened before stays as history.
 */
public record Disruption(Instant at, Set<String> chargersOutOfService, Map<String, PowerKw> gridCapacityOverrides) {
    public Disruption {
        Objects.requireNonNull(at, "at");
        chargersOutOfService = Set.copyOf(chargersOutOfService);
        gridCapacityOverrides = Map.copyOf(gridCapacityOverrides);
    }
}
