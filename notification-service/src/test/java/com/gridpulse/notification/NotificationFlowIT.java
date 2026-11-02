package com.gridpulse.notification;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real Kafka + real Postgres: idempotency, dead-lettering and tenant isolation of the notification feed. */
@SpringBootTest(properties = "notification.topic=test.session-events")
@AutoConfigureMockMvc
class NotificationFlowIT {
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.9.1");

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "&currentSchema=notification");
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        r.add("notification.jwt-secret", () -> "notification-test-signing-key-not-a-real-secret");
    }

    @Autowired KafkaTemplate<Object, Object> kafka;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    private static String event(UUID eventId, String type, UUID tenant, UUID session) {
        return """
                {"eventId":"%s","eventType":"%s","tenantId":"%s","aggregateType":"charging_session","aggregateId":"%s",
                 "occurredAt":"2026-01-06T01:00:00Z","newFieldFromAFutureVersion":true,
                 "data":{"sessionId":"%s","vehicleId":"v-1","chargerId":"c-1","reason":"charger fault E42"}}
                """.formatted(eventId, type, tenant, session, session);
    }

    private int notifications(UUID tenant) {
        return jdbc.queryForObject("SELECT count(*) FROM notification WHERE tenant_id = ?", Integer.class, tenant);
    }

    @Test
    void duplicatesAreIgnoredAndOnlyNotableEventsNotify() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID session = UUID.randomUUID();
        UUID interrupted = UUID.randomUUID();
        UUID started = UUID.randomUUID();
        kafka.send("test.session-events", session.toString(), event(started, "SESSION_STARTED", tenant, session));
        kafka.send("test.session-events", session.toString(), event(interrupted, "SESSION_INTERRUPTED", tenant, session));
        kafka.send("test.session-events", session.toString(), event(interrupted, "SESSION_INTERRUPTED", tenant, session)); // redelivery
        kafka.flush();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM processed_event WHERE event_id IN (?, ?)", Integer.class, interrupted, started))
                .isEqualTo(2));
        assertThat(notifications(tenant)).isEqualTo(1);

        mvc.perform(get("/api/v1/notifications").with(jwt().jwt(j -> j.claim("tenant_id", tenant.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].severity").value("WARNING"));
        // another tenant sees nothing
        mvc.perform(get("/api/v1/notifications").with(jwt().jwt(j -> j.claim("tenant_id", UUID.randomUUID().toString()))))
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
    }

    @Test
    void malformedMessagesGoToTheDeadLetterTopicAndDoNotBlockOthers() {
        UUID tenant = UUID.randomUUID();
        UUID session = UUID.randomUUID();
        kafka.send("test.session-events", "bad", "this is not json");
        kafka.send("test.session-events", session.toString(), event(UUID.randomUUID(), "SESSION_COMPLETED", tenant, session));
        kafka.flush();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(notifications(tenant)).isEqualTo(1));

        List<String> dead = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "dlt-check-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"), new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of("test.session-events-dlt"));
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(r -> dead.add(r.value()));
                assertThat(dead).contains("this is not json");
            });
        }
    }
}
