package com.gridpulse.events;

import com.gridpulse.PostgresIntegrationTest;
import com.gridpulse.fleet.Fleet;
import com.gridpulse.fleet.FleetRepository;
import com.gridpulse.grid.GridConnection;
import com.gridpulse.grid.GridConnectionRepository;
import com.gridpulse.session.ChargingSession;
import com.gridpulse.session.ChargingSessionService;
import com.gridpulse.shared.domain.ConnectorType;
import com.gridpulse.shared.domain.EnergyKwh;
import com.gridpulse.shared.domain.PowerKw;
import com.gridpulse.shared.domain.StateOfCharge;
import com.gridpulse.shared.domain.TimeWindow;
import com.gridpulse.station.Charger;
import com.gridpulse.station.ChargerRepository;
import com.gridpulse.station.ChargingStation;
import com.gridpulse.station.ChargingStationRepository;
import com.gridpulse.vehicle.Vehicle;
import com.gridpulse.vehicle.VehicleRepository;
import com.jayway.jsonpath.JsonPath;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Transactional outbox end to end: session changes write events atomically, and the relay delivers them to a real
 * Kafka broker (Testcontainers) keyed by session id, marking each row published only after the broker acknowledged it.
 */
@SpringBootTest(properties = {"gridpulse.events.kafka.enabled=true", "gridpulse.events.relay-interval=200ms",
        "gridpulse.events.topic=test.session-events"})
class SessionEventsIT extends PostgresIntegrationTest {
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.9.1");

    static {
        KAFKA.start();
    }

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry r) {
        r.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    private static final Instant T = Instant.parse("2026-01-05T22:00:00Z");

    @Autowired ChargingSessionService sessions;
    @Autowired JdbcTemplate jdbc;
    @Autowired FleetRepository fleets;
    @Autowired VehicleRepository vehicles;
    @Autowired GridConnectionRepository grids;
    @Autowired ChargingStationRepository stations;
    @Autowired ChargerRepository chargers;

    UUID tenant;
    UUID vehicle;
    UUID otherVehicle;
    UUID charger;

    @BeforeEach
    void seed() {
        tenant = UUID.randomUUID();
        jdbc.update("INSERT INTO tenant (id, name) VALUES (?, ?)", tenant, "events-" + tenant);
        Fleet fleet = fleets.save(new Fleet(tenant, "Vans"));
        vehicle = vehicles.save(van(fleet, randomVin())).getId();
        otherVehicle = vehicles.save(van(fleet, randomVin())).getId();
        GridConnection grid = grids.save(new GridConnection(tenant, "Feed", PowerKw.of(300)));
        ChargingStation station = stations.save(new ChargingStation(tenant, grid.getId(), "Depot"));
        charger = chargers.save(new Charger(tenant, station.getId(), "A1", ConnectorType.CCS1, PowerKw.of(150))).getId();
    }

    private Vehicle van(Fleet fleet, String vin) {
        return new Vehicle(tenant, fleet.getId(), vin, "Synthetic Van", EnergyKwh.of(100),
                PowerKw.of(150), ConnectorType.CCS1, new StateOfCharge(30));
    }

    private static String randomVin() {
        String alphabet = "ABCDEFGHJKLMNPRSTUVWXYZ0123456789";
        java.util.Random rnd = new java.util.Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 17; i++) sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
        return sb.toString();
    }

    private TimeWindow window(int from, int to) {
        return new TimeWindow(T.plusSeconds(from * 60L), T.plusSeconds(to * 60L));
    }

    private List<String> eventTypes(UUID sessionId) {
        return jdbc.queryForList("SELECT event_type FROM outbox_event WHERE aggregate_id = ? ORDER BY created_at",
                String.class, sessionId);
    }

    @Test
    void lifecycleChangesAreDeliveredToKafkaInOrderAndMarkedPublished() {
        ChargingSession s = sessions.reserve(tenant, "tester", vehicle, charger, window(0, 60), PowerKw.of(100));
        sessions.start(tenant, "tester", s.getId());
        sessions.interrupt(tenant, "tester", s.getId(), "charger fault E42");

        assertThat(eventTypes(s.getId())).containsExactly("SESSION_RESERVED", "SESSION_STARTED", "SESSION_INTERRUPTED");

        List<ConsumerRecord<String, String>> received = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"),
                new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of("test.session-events"));
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(r -> {
                    if (r.key().equals(s.getId().toString())) received.add(r);
                });
                assertThat(received).hasSize(3);
            });
        }
        assertThat(received).extracting(r -> (String) JsonPath.read(r.value(), "$.eventType"))
                .containsExactly("SESSION_RESERVED", "SESSION_STARTED", "SESSION_INTERRUPTED");
        assertThat((String) JsonPath.read(received.get(2).value(), "$.data.reason")).isEqualTo("charger fault E42");
        assertThat((String) JsonPath.read(received.get(0).value(), "$.tenantId")).isEqualTo(tenant.toString());

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE aggregate_id = ? AND published_at IS NULL",
                Integer.class, s.getId())).isZero());
    }

    @Test
    void aRejectedReservationLeavesNoEvent() {
        sessions.reserve(tenant, "tester", vehicle, charger, window(0, 60), PowerKw.of(100));
        Integer before = jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE tenant_id = ?", Integer.class, tenant);
        // overlaps on the same charger: the database rejects it, so the transaction (and its event) rolls back
        assertThatThrownBy(() -> sessions.reserve(tenant, "tester", otherVehicle, charger, window(30, 90), PowerKw.of(100)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE tenant_id = ?", Integer.class, tenant))
                .isEqualTo(before);
    }
}
