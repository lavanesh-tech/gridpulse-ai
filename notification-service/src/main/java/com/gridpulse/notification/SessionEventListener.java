package com.gridpulse.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent consumer. "Mark processed" and "write notification" commit in one database transaction, and the Kafka
 * offset is committed only after the method returns. A redelivered event finds its id already in processed_event and
 * is skipped, so a crash between the two never produces a duplicate or a lost notification.
 */
@Component
class SessionEventListener {
    private static final Logger log = LoggerFactory.getLogger(SessionEventListener.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final MeterRegistry meters;

    SessionEventListener(JdbcTemplate jdbc, ObjectMapper mapper, MeterRegistry meters) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.meters = meters;
    }

    @KafkaListener(topics = "${notification.topic}")
    @Transactional
    public void onEvent(String payload) throws Exception {
        SessionEvent e = mapper.readValue(payload, SessionEvent.class); // malformed JSON -> dead-letter topic
        e.validate();
        int fresh = jdbc.update("INSERT INTO processed_event (event_id) VALUES (?) ON CONFLICT DO NOTHING", e.eventId());
        if (fresh == 0) {
            meters.counter("notification.events", "result", "duplicate").increment();
            log.debug("duplicate event {} skipped", e.eventId());
            return;
        }
        NotificationRules.draft(e).ifPresent(d -> jdbc.update("""
                INSERT INTO notification (event_id, tenant_id, session_id, severity, message) VALUES (?, ?, ?, ?, ?)
                """, e.eventId(), e.tenantId(), e.aggregateId(), d.severity(), d.message()));
        meters.counter("notification.events", "result", "processed", "type", e.eventType()).increment();
    }
}
