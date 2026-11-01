package com.gridpulse.scheduling;

import java.util.List;
import java.util.Objects;

public record ChargingSchedule(String algorithm, List<ScheduledSession> sessions, List<UnmetRequest> unmet) {
    public ChargingSchedule {
        Objects.requireNonNull(algorithm, "algorithm");
        sessions = List.copyOf(sessions);
        unmet = List.copyOf(unmet);
    }
}
