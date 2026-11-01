package com.gridpulse.scheduling;

import com.gridpulse.shared.domain.PowerKw;
import com.gridpulse.shared.domain.TimeWindow;

import java.util.Objects;

/** A vehicle charging on a charger at constant power during a time window. */
public record ScheduledSession(String vehicleId, String chargerId, TimeWindow window, PowerKw power) {
    public ScheduledSession {
        Objects.requireNonNull(vehicleId, "vehicleId");
        Objects.requireNonNull(chargerId, "chargerId");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(power, "power");
    }
}
