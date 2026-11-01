package com.gridpulse.scheduling;

import com.gridpulse.shared.domain.PowerKw;
import com.gridpulse.shared.domain.TimeWindow;

import java.util.Objects;

/**
 * Capacity already committed outside this planning run (an existing reservation or a session in progress).
 * The scheduler plans around it; the validator counts it towards grid load and overlaps.
 * {@code vehicleId} may be null when the booked vehicle is not part of this problem.
 */
public record FixedBooking(String vehicleId, String chargerId, TimeWindow window, PowerKw power) {
    public FixedBooking {
        Objects.requireNonNull(chargerId, "chargerId");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(power, "power");
    }
}
