package com.gridpulse.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** The tools against a mocked GridPulse API: what they send, how they summarise, and how they handle auth. */
class GridPulseToolsTest {
    private static final String API = "http://gridpulse.test";
    private static final String TOKEN = "{\"accessToken\":\"t1\"}";
    private static final String VEHICLES = """
            [{"id":"v-1","model":"eTransit","vin":"1HGCM82633A000001","connectorType":"CCS1","stateOfChargePct":20,"batteryCapacityKwh":89},
             {"id":"v-2","model":"ID.Buzz","vin":"1HGCM82633A000002","connectorType":"NACS","stateOfChargePct":50,"batteryCapacityKwh":82}]
            """;
    private static final String PREVIEW = """
            {"schedule":{"sessions":[{"vehicleId":"v-1"}],"unmet":[{"vehicleId":"v-2","reason":"NO_COMPATIBLE_CHARGER"}]},
             "violations":[],
             "metrics":{"vehiclesNeedingCharge":2,"deadlinesMet":1,"cost":{"totalCost":5.87,"averagePricePerKwh":0.11}}}
            """;

    MockRestServiceServer server;
    GridPulseTools tools;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        GridPulseClient client = new GridPulseClient(builder, new GridPulseProperties(API, "viewer", "pw"));
        // 10:00 in New York on 5 Jan 2026 -> tonight is 18:00 EST = 23:00Z
        tools = new GridPulseTools(client, Clock.fixed(Instant.parse("2026-01-05T15:00:00Z"), ZoneId.of("America/New_York")));
    }

    @Test
    void plansTonightForTheWholeFleetAndExplainsWhoIsShort() {
        server.expect(once(), requestTo(API + "/api/v1/auth/token")).andRespond(withSuccess(TOKEN, MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/api/v1/fleets/f-1/vehicles")).andExpect(header("Authorization", "Bearer t1"))
                .andRespond(withSuccess(VEHICLES, MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/api/v1/schedules/preview")).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.algorithm").value("PRIORITY_SLACK"))
                .andExpect(jsonPath("$.horizonStart").value("2026-01-05T23:00:00Z"))
                .andExpect(jsonPath("$.slotCount").value(48))
                .andExpect(jsonPath("$.vehicles.length()").value(2))
                .andRespond(withSuccess(PREVIEW, MediaType.APPLICATION_JSON));

        GridPulseTools.PlanSummary s = tools.planTonight("f-1", null);

        assertThat(s.deadlinesMet()).isEqualTo(1);
        assertThat(s.vehiclesNeedingCharge()).isEqualTo(2);
        assertThat(s.estimatedCost()).isEqualTo(5.87);
        assertThat(s.vehiclesShort()).containsExactly("ID.Buzz ...0002: NO_COMPATIBLE_CHARGER");
        server.verify();
    }

    @Test
    void signsInAgainWhenTheTokenExpires() {
        server.expect(requestTo(API + "/api/v1/auth/token")).andRespond(withSuccess(TOKEN, MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/api/v1/fleets")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo(API + "/api/v1/auth/token")).andRespond(withSuccess("{\"accessToken\":\"t2\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/api/v1/fleets")).andExpect(header("Authorization", "Bearer t2"))
                .andRespond(withSuccess("[{\"id\":\"f-1\",\"name\":\"Night vans\"}]", MediaType.APPLICATION_JSON));

        assertThat(tools.listFleets()).containsExactly(new GridPulseTools.Fleet("f-1", "Night vans"));
        server.verify();
    }

    @Test
    void rejectsUnknownAlgorithmsWithoutCallingTheApi() {
        assertThatThrownBy(() -> tools.planTonight("f-1", "DROP TABLE")).isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }

    @Test
    void afterSixInTheEveningTonightMeansTomorrowNight() {
        GridPulseTools late = new GridPulseTools(null,
                Clock.fixed(Instant.parse("2026-01-05T23:30:00Z"), ZoneId.of("America/New_York"))); // 18:30 local
        assertThat(late.tonight()[0]).isEqualTo(Instant.parse("2026-01-06T23:00:00Z"));
    }

    @Test
    void exposesExactlyTheReadOnlyTools() {
        ToolCallback[] callbacks = MethodToolCallbackProvider.builder().toolObjects(tools).build().getToolCallbacks();
        assertThat(Arrays.stream(callbacks).map(c -> c.getToolDefinition().name()))
                .containsExactlyInAnyOrder("listFleets", "listVehicles", "planTonight", "compareStrategies", "askDepotDocuments");
    }
}
