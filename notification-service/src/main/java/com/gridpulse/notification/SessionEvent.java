package com.gridpulse.notification;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;
import java.util.UUID;

/**
 * The envelope GridPulse publishes. Unknown fields are ignored so the producer can add fields without breaking
 * this consumer (tolerant reader).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SessionEvent(UUID eventId, String eventType, UUID tenantId, UUID aggregateId, String occurredAt,
                           Map<String, Object> data) {

    public void validate() {
        if (eventId == null || eventType == null || tenantId == null || aggregateId == null || data == null) {
            throw new IllegalArgumentException("event is missing required fields");
        }
    }

    String field(String name) {
        Object v = data.get(name);
        return v == null ? "?" : v.toString();
    }
}
