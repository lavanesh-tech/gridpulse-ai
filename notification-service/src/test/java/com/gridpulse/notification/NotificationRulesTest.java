package com.gridpulse.notification;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationRulesTest {
    private static SessionEvent event(String type, Map<String, Object> data) {
        return new SessionEvent(UUID.randomUUID(), type, UUID.randomUUID(), UUID.randomUUID(), "2026-01-06T01:00:00Z", data);
    }

    @Test
    void interruptionIsAWarningThatNamesVehicleChargerAndReason() {
        var d = NotificationRules.draft(event("SESSION_INTERRUPTED",
                Map.of("vehicleId", "v-1", "chargerId", "c-9", "reason", "charger fault E42"))).orElseThrow();
        assertThat(d.severity()).isEqualTo("WARNING");
        assertThat(d.message()).contains("v-1", "c-9", "charger fault E42");
    }

    @Test
    void routineEventsProduceNoNotification() {
        assertThat(NotificationRules.draft(event("SESSION_RESERVED", Map.of()))).isEmpty();
        assertThat(NotificationRules.draft(event("SESSION_STARTED", Map.of()))).isEmpty();
        assertThat(NotificationRules.draft(event("SOMETHING_NEW", Map.of()))).isEmpty();
    }

    @Test
    void cancellationAndCompletionAreInfo() {
        assertThat(NotificationRules.draft(event("SESSION_CANCELLED", Map.of("vehicleId", "v")))).get()
                .extracting(NotificationRules.Draft::severity).isEqualTo("INFO");
        assertThat(NotificationRules.draft(event("SESSION_COMPLETED", Map.of("vehicleId", "v")))).get()
                .extracting(NotificationRules.Draft::severity).isEqualTo("INFO");
    }

    @Test
    void incompleteEventsAreRejected() {
        assertThatThrownBy(() -> new SessionEvent(null, "SESSION_STARTED", UUID.randomUUID(), UUID.randomUUID(), null,
                Map.of()).validate()).isInstanceOf(IllegalArgumentException.class);
    }
}
