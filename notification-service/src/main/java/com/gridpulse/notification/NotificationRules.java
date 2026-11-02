package com.gridpulse.notification;

import java.util.Optional;

/** Which session events deserve a notification, and how it reads. Pure function, unit-tested. */
final class NotificationRules {
    private NotificationRules() {
    }

    record Draft(String severity, String message) {
    }

    static Optional<Draft> draft(SessionEvent e) {
        String charger = e.field("chargerId");
        String vehicle = e.field("vehicleId");
        return switch (e.eventType()) {
            case "SESSION_INTERRUPTED" -> Optional.of(new Draft("WARNING", "Charging interrupted for vehicle " + vehicle
                    + " on charger " + charger + ": " + e.field("reason") + ". Re-plan to check departures."));
            case "SESSION_CANCELLED" -> Optional.of(new Draft("INFO",
                    "Reservation cancelled for vehicle " + vehicle + " (" + e.field("startsAt") + ")."));
            case "SESSION_COMPLETED" -> Optional.of(new Draft("INFO", "Charging completed for vehicle " + vehicle + "."));
            default -> Optional.empty(); // reserved/started are routine: recorded as processed, no notification
        };
    }
}
