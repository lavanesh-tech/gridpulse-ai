package com.gridpulse.scheduling;

import com.gridpulse.shared.domain.PowerKw;

import java.util.Objects;

/** Maximum total power a grid connection can supply at any instant. */
public record GridLimit(String gridId, PowerKw capacity) {
    public GridLimit {
        Objects.requireNonNull(gridId, "gridId");
        Objects.requireNonNull(capacity, "capacity");
    }
}
