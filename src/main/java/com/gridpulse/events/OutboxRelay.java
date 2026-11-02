package com.gridpulse.events;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Moves outbox rows to Kafka. Rows are claimed with FOR UPDATE SKIP LOCKED, so several app instances can relay in
 * parallel without sending the same row twice at the same time. A row is marked published only after Kafka
 * acknowledged it; if the send fails the transaction rolls back and the row is retried on the next run.
 * Result: at-least-once delivery, in creation order per aggregate (the key), and consumers de-duplicate by eventId.
 */
@Component
@ConditionalOnProperty(name = "gridpulse.events.kafka.enabled", havingValue = "true")
class OutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    static final int BATCH = 100;

    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final EventsProperties props;
    private final MeterRegistry meters;

    OutboxRelay(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka, EventsProperties props, MeterRegistry meters) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.props = props;
        this.meters = meters;
    }

    private record Row(UUID id, UUID aggregateId, String payload) {
    }

    @Scheduled(fixedDelayString = "${gridpulse.events.relay-interval:1s}")
    @Transactional
    public void relay() {
        List<Row> rows = jdbc.query("""
                SELECT id, aggregate_id, payload::text AS payload FROM outbox_event
                WHERE published_at IS NULL ORDER BY created_at LIMIT ? FOR UPDATE SKIP LOCKED
                """, (rs, i) -> new Row(rs.getObject("id", UUID.class), rs.getObject("aggregate_id", UUID.class),
                rs.getString("payload")), BATCH);
        for (Row r : rows) {
            try {
                kafka.send(props.topic(), r.aggregateId().toString(), r.payload()).get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                meters.counter("gridpulse.outbox.failures").increment();
                log.warn("Kafka send failed for outbox event {}; will retry: {}", r.id(), e.toString());
                throw new IllegalStateException("outbox relay failed", e); // rolls back: nothing is marked published
            }
            jdbc.update("UPDATE outbox_event SET published_at = now() WHERE id = ?", r.id());
            meters.counter("gridpulse.outbox.published").increment();
        }
    }
}
