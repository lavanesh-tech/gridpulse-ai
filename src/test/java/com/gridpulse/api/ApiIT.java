package com.gridpulse.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gridpulse.PostgresIntegrationTest;
import com.gridpulse.identity.AppUser;
import com.gridpulse.identity.AppUserRepository;
import com.gridpulse.identity.Role;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end HTTP tests: real security filter chain, real JWTs, real Postgres. */
@SpringBootTest
@AutoConfigureMockMvc
class ApiIT extends PostgresIntegrationTest {
    private static final String PASSWORD = "correct-horse-battery-staple";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;

    UUID tenantA;
    UUID tenantB;
    String prefix;

    @BeforeEach
    void setUp() {
        prefix = UUID.randomUUID().toString().substring(0, 8);
        tenantA = newTenant("A");
        tenantB = newTenant("B");
        for (Role r : Role.values()) {
            users.save(new AppUser(tenantA, user("a", r), encoder.encode(PASSWORD), r));
        }
        users.save(new AppUser(tenantB, user("b", Role.ADMIN), encoder.encode(PASSWORD), Role.ADMIN));
    }

    private UUID newTenant(String label) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO tenant (id, name) VALUES (?, ?)", id, label + "-" + id);
        return id;
    }

    private String user(String tenant, Role role) {
        return prefix + "-" + tenant + "-" + role.name().toLowerCase();
    }

    private String token(String username) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private ResultActions postJson(String url, String token, Object body) throws Exception {
        return mvc.perform(post(url).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private String id(ResultActions r) throws Exception {
        return JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.id");
    }

    @Test
    void healthIsPublicButApiNeedsToken() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/fleets")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/fleets").header("Authorization", "Bearer not-a-jwt")).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordAndUnknownUserAreBoth401() throws Exception {
        mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", user("a", Role.ADMIN), "password", "wrong"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", "nobody-" + prefix, "password", PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void viewerCannotWriteButCanRead() throws Exception {
        String viewer = token(user("a", Role.VIEWER));
        postJson("/api/v1/fleets", viewer, Map.of("name", "Nope")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/fleets").header("Authorization", "Bearer " + viewer)).andExpect(status().isOk());
    }

    @Test
    void validationErrorsReturnProblemDetailWithFieldErrors() throws Exception {
        String manager = token(user("a", Role.FLEET_MANAGER));
        postJson("/api/v1/fleets", manager, Map.of("name", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void tenantsCannotSeeEachOthersData() throws Exception {
        String managerA = token(user("a", Role.FLEET_MANAGER));
        String adminB = token(user("b", Role.ADMIN));
        String fleetA = id(postJson("/api/v1/fleets", managerA, Map.of("name", "Night shift")).andExpect(status().isCreated()));

        mvc.perform(get("/api/v1/fleets/" + fleetA).header("Authorization", "Bearer " + adminB))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/fleets").header("Authorization", "Bearer " + adminB))
                .andExpect(jsonPath("$", hasSize(0)));
        postJson("/api/v1/fleets/" + fleetA + "/vehicles", adminB, vehicleBody("CCS1"))
                .andExpect(status().isNotFound());
    }

    private Map<String, Object> vehicleBody(String connector) {
        String vin = (UUID.randomUUID().toString().replaceAll("[^0-9]", "") + "00000000000000000").substring(0, 17);
        return Map.of("vin", vin, "model", "Synthetic Van", "batteryCapacityKwh", 100,
                "maxChargeRateKw", 150, "connectorType", connector, "stateOfChargePct", 20);
    }

    @Test
    void fullFlowFromSetupToReservationAndSchedulePreview() throws Exception {
        String admin = token(user("a", Role.ADMIN));
        String manager = token(user("a", Role.FLEET_MANAGER));
        String operator = token(user("a", Role.OPERATOR));

        String grid = id(postJson("/api/v1/grid-connections", admin, Map.of("name", "Feed", "capacityLimitKw", 200))
                .andExpect(status().isCreated()));
        String station = id(postJson("/api/v1/stations", admin, Map.of("gridConnectionId", grid, "name", "Depot"))
                .andExpect(status().isCreated()));
        String charger = id(postJson("/api/v1/stations/" + station + "/chargers", admin,
                Map.of("label", "C1", "connectorType", "CCS1", "maxPowerKw", 150)).andExpect(status().isCreated()));
        String fleet = id(postJson("/api/v1/fleets", manager, Map.of("name", "Vans")).andExpect(status().isCreated()));
        String ccs = id(postJson("/api/v1/fleets/" + fleet + "/vehicles", manager, vehicleBody("CCS1")).andExpect(status().isCreated()));
        String nacs = id(postJson("/api/v1/fleets/" + fleet + "/vehicles", manager, vehicleBody("NACS")).andExpect(status().isCreated()));

        Map<String, Object> booking = Map.of("vehicleId", ccs, "chargerId", charger,
                "startsAt", "2026-01-05T22:00:00Z", "endsAt", "2026-01-05T23:00:00Z", "powerKw", 100);
        postJson("/api/v1/sessions", operator, booking).andExpect(status().isCreated());
        postJson("/api/v1/sessions", operator, booking).andExpect(status().isConflict());
        postJson("/api/v1/sessions", operator, Map.of("vehicleId", nacs, "chargerId", charger,
                "startsAt", "2026-01-06T01:00:00Z", "endsAt", "2026-01-06T02:00:00Z", "powerKw", 100))
                .andExpect(status().isUnprocessableEntity());

        postJson("/api/v1/schedules/preview", manager, Map.of(
                "horizonStart", "2026-01-06T00:00:00Z", "slotMinutes", 15, "slotCount", 32, "algorithm", "PRIORITY_SLACK",
                "vehicles", List.of(Map.of("vehicleId", ccs, "targetSocPct", 80,
                        "arrival", "2026-01-06T00:00:00Z", "departure", "2026-01-06T06:00:00Z", "priority", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.violations", hasSize(0)))
                .andExpect(jsonPath("$.schedule.unmet", hasSize(0)))
                .andExpect(jsonPath("$.metrics.deadlinesMet").value(1));

        Integer audits = jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE tenant_id = ? AND action = 'RESERVE_SESSION'",
                Integer.class, tenantA);
        assertThat(audits).isEqualTo(1);
        mvc.perform(get("/api/v1/audit-events").header("Authorization", "Bearer " + manager)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/audit-events").header("Authorization", "Bearer " + admin)).andExpect(status().isOk());
    }
}
