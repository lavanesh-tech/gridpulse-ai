package com.gridpulse.scheduling;

import java.time.Instant;
import java.util.List;

/**
 * Outcome of re-planning after a disruption. {@code schedule} = sessions kept from before the disruption
 * (history, possibly cut short) + newly planned sessions from {@code replannedFrom}.
 */
public record RecalculationResult(ChargingSchedule schedule, Instant replannedFrom, int sessionsKept,
                                  int sessionsInterrupted, int sessionsReplanned, List<String> newlyUnmetVehicles,
                                  List<Violation> violations) {
    public RecalculationResult {
        newlyUnmetVehicles = List.copyOf(newlyUnmetVehicles);
        violations = List.copyOf(violations);
    }
}
