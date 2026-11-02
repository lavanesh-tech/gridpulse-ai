package com.gridpulse.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gridpulse.PostgresIntegrationTest;
import com.gridpulse.identity.AppUser;
import com.gridpulse.identity.AppUserRepository;
import com.gridpulse.identity.Role;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Health probes, who may read metrics, the custom business metrics, and request-id propagation. */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability
class ObservabilityIT extends PostgresIntegrationTest {
    private static final String PASSWORD = "correct-horse-battery-staple";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;

    String prefix;

    @BeforeEach
    void setUp() {
        prefix = "obs-" + UUID.randomUUID().toString().substring(0, 8);
        UUID tenant = UUID.randomUUID();
        jdbc.update("INSERT INTO tenant (id, name) VALUES (?, ?)", tenant, prefix);
        users.save(new AppUser(tenant, prefix + "-admin", encoder.encode(PASSWORD), Role.ADMIN));
        users.save(new AppUser(tenant, prefix + "-viewer", encoder.encode(PASSWORD), Role.VIEWER));
    }

    private String token(String role) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", prefix + "-" + role, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private ResultActions postJson(String url, String token, Object body) throws Exception {
        return mvc.perform(post(url).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private String id(ResultActions r) throws Exception {
        return JsonPath.read(r.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String prometheus(String admin) throws Exception {
        return mvc.perform(get("/actuator/prometheus").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void healthProbesArePublicAndDetailsAreNot() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health").header("Authorization", "Bearer " + token("admin")))
                .andExpect(jsonPath("$.components.db.status").value("UP"));
    }

    @Test
    void metricsAreAdminOnly() throws Exception {
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/prometheus").header("Authorization", "Bearer " + token("viewer")))
                .andExpect(status().isForbidden());
        assertThat(prometheus(token("admin"))).contains("http_server_requests_seconds");
    }

    @Test
    void businessMetricsAreRecorded() throws Exception {
        String admin = token("admin");
        // Nothing ingested for this tenant: the advisor refuses without calling a model.
        postJson("/api/v1/advisor/ask", admin, Map.of("question", "When is the off-peak tariff period?"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.grounded").value(false));

        String grid = id(postJson("/api/v1/grid-connections", admin, Map.of("name", "Feed", "capacityLimitKw", 200)));
        String station = id(postJson("/api/v1/stations", admin, Map.of("gridConnectionId", grid, "name", "Depot")));
        id(postJson("/api/v1/stations/" + station + "/chargers", admin,
                Map.of("label", "C1", "connectorType", "CCS1", "maxPowerKw", 150)));
        String fleet = id(postJson("/api/v1/fleets", admin, Map.of("name", "Vans")));
        String vin = (UUID.randomUUID().toString().replaceAll("[^0-9]", "") + "00000000000000000").substring(0, 17);
        String vehicle = id(postJson("/api/v1/fleets/" + fleet + "/vehicles", admin, Map.of("vin", vin,
                "model", "Synthetic Van", "batteryCapacityKwh", 100, "maxChargeRateKw", 150,
                "connectorType", "CCS1", "stateOfChargePct", 20)));
        postJson("/api/v1/schedules/preview", admin, Map.of(
                "horizonStart", "2026-01-06T00:00:00Z", "slotMinutes", 15, "slotCount", 32, "algorithm", "PRIORITY_SLACK",
                "vehicles", List.of(Map.of("vehicleId", vehicle, "targetSocPct", 80,
                        "arrival", "2026-01-06T00:00:00Z", "departure", "2026-01-06T06:00:00Z", "priority", 1))))
                .andExpect(status().isOk());

        String metrics = prometheus(admin);
        assertThat(metrics)
                .containsPattern("gridpulse_advisor_questions_total\\{[^}]*outcome=\"refused_no_sources\"")
                .containsPattern("gridpulse_schedule_compute_seconds_count\\{[^}]*algorithm=\"priority-slack\"")
                .containsPattern("gridpulse_schedule_vehicles_total\\{[^}]*outcome=\"planned\"");
    }

    @Test
    void requestIdIsEchoedOrGenerated() throws Exception {
        mvc.perform(get("/actuator/health").header("X-Request-Id", "trace-abc-123"))
                .andExpect(header().string("X-Request-Id", "trace-abc-123"));
        String generated = mvc.perform(get("/actuator/health").header("X-Request-Id", "bad value!"))
                .andReturn().getResponse().getHeader("X-Request-Id");
        assertThat(generated).matches("[0-9a-f-]{36}");
    }
}
