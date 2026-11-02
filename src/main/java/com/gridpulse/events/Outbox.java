package com.gridpulse.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Records an integration event as part of the caller's transaction (it must already be running). */
@Service
public class Outbox {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public Outbox(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /**
     * @param data event-specific fields; the envelope (eventId, eventType, tenantId, aggregateId, occurredAt) is added here
     * @return the event id, which consumers use to de-duplicate (delivery is at-least-once)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID record(UUID tenantId, String aggregateType, UUID aggregateId, String eventType, Map<String, ?> data) {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", eventType);
        envelope.put("tenantId", tenantId.toString());
        envelope.put("aggregateType", aggregateType);
        envelope.put("aggregateId", aggregateId.toString());
        envelope.put("occurredAt", java.time.Instant.now().toString());
        envelope.put("data", data);
        String json;
        try {
            json = mapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("event not serialisable", e);
        }
        jdbc.update("""
                INSERT INTO outbox_event (id, tenant_id, aggregate_type, aggregate_id, event_type, payload)
                VALUES (?, ?, ?, ?, ?, ?::jsonb)
                """, eventId, tenantId, aggregateType, aggregateId, eventType, json);
        return eventId;
    }
}
