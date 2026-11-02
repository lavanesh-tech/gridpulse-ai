package com.gridpulse.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The MCP tools. All are read-only: planning calls the preview endpoint, which never books anything.
 * Results are compact summaries, so an assistant gets the facts without megabytes of schedule JSON.
 */
@Component
public class GridPulseTools {
    static final Set<String> ALGORITHMS = Set.of("BASELINE", "PRIORITY_SLACK", "PRIORITY_SLACK_COST");
    static final int SLOT_MINUTES = 15;

    private final GridPulseClient api;
    private final Clock clock;

    public GridPulseTools(GridPulseClient api) {
        this(api, Clock.systemDefaultZone());
    }

    GridPulseTools(GridPulseClient api, Clock clock) {
        this.api = api;
        this.clock = clock;
    }

    public record Fleet(String id, String name) {
    }

    public record VehicleSummary(String id, String label, String connector, int stateOfChargePct, double batteryKwh) {
    }

    public record PlanSummary(String algorithm, String window, int vehiclesNeedingCharge, int deadlinesMet,
                              double estimatedCost, double averagePricePerKwh, int hardConstraintViolations,
                              int sessions, List<String> vehiclesShort) {
    }

    @Tool(description = "List the fleets of the signed-in GridPulse user's organisation.")
    public List<Fleet> listFleets() {
        List<Fleet> out = new ArrayList<>();
        api.get("/api/v1/fleets").forEach(f -> out.add(new Fleet(f.path("id").asText(), f.path("name").asText())));
        return out;
    }

    @Tool(description = "List the vehicles in a fleet with connector type, current state of charge and battery size.")
    public List<VehicleSummary> listVehicles(@ToolParam(description = "fleet id from listFleets") String fleetId) {
        List<VehicleSummary> out = new ArrayList<>();
        api.get("/api/v1/fleets/{id}/vehicles", fleetId.strip()).forEach(v -> out.add(new VehicleSummary(
                v.path("id").asText(), label(v), v.path("connectorType").asText(),
                v.path("stateOfChargePct").asInt(), v.path("batteryCapacityKwh").asDouble())));
        return out;
    }

    @Tool(description = "Plan tonight's charging (18:00 to 06:00 local time) for every vehicle in a fleet, each to 80% "
            + "with normal priority. Returns how many vehicles leave on time, the estimated energy cost and which "
            + "vehicles would be short and why. Read-only: nothing is booked.")
    public PlanSummary planTonight(@ToolParam(description = "fleet id from listFleets") String fleetId,
                                   @ToolParam(description = "PRIORITY_SLACK (deadlines first, default), "
                                           + "PRIORITY_SLACK_COST (cheapest first) or BASELINE", required = false)
                                   String algorithm) {
        String a = algorithm == null || algorithm.isBlank() ? "PRIORITY_SLACK" : algorithm.strip().toUpperCase();
        if (!ALGORITHMS.contains(a)) throw new IllegalArgumentException("unknown algorithm " + algorithm);
        JsonNode vehicles = api.get("/api/v1/fleets/{id}/vehicles", fleetId.strip());
        if (vehicles.isEmpty()) throw new IllegalArgumentException("fleet has no vehicles");
        Map<String, String> names = new LinkedHashMap<>();
        vehicles.forEach(v -> names.put(v.path("id").asText(), label(v)));
        Instant[] window = tonight();
        return summarise(a, window, names, api.post("/api/v1/schedules/preview", previewBody(a, window, names.keySet())));
    }

    @Tool(description = "Compare all three scheduling strategies for tonight on the same fleet: deadlines met, "
            + "estimated cost and vehicles left short for each. Read-only.")
    public List<PlanSummary> compareStrategies(@ToolParam(description = "fleet id from listFleets") String fleetId) {
        return List.of(planTonight(fleetId, "BASELINE"), planTonight(fleetId, "PRIORITY_SLACK"),
                planTonight(fleetId, "PRIORITY_SLACK_COST"));
    }

    @Tool(description = "Ask a question about tariffs, charging policy or outage procedures. Answered only from the "
            + "organisation's own documents, with the titles of the sources used; refuses if they do not cover it.")
    public Map<String, Object> askDepotDocuments(@ToolParam(description = "the question") String question) {
        JsonNode a = api.post("/api/v1/advisor/ask", Map.of("question", question));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("answer", a.path("answer").isNull() ? "No language model is configured on the server; see sources."
                : a.path("answer").asText());
        out.put("grounded", a.path("grounded").asBoolean());
        List<String> sources = new ArrayList<>();
        a.path("sources").forEach(s -> sources.add("S" + s.path("ref").asText() + ": " + s.path("title").asText()
                + " - " + s.path("text").asText()));
        out.put("sources", sources);
        return out;
    }

    /** Tonight 18:00 to tomorrow 06:00 in this machine's time zone; after 18:00 it means tomorrow night. */
    Instant[] tonight() {
        ZonedDateTime now = ZonedDateTime.now(clock);
        LocalDate day = now.toLocalTime().isBefore(LocalTime.of(18, 0)) ? now.toLocalDate() : now.toLocalDate().plusDays(1);
        ZonedDateTime from = day.atTime(18, 0).atZone(clock.getZone());
        return new Instant[]{from.toInstant(), from.plusHours(12).toInstant()};
    }

    static Map<String, Object> previewBody(String algorithm, Instant[] window, Iterable<String> vehicleIds) {
        List<Map<String, Object>> needs = new ArrayList<>();
        for (String id : vehicleIds) {
            needs.add(Map.of("vehicleId", id, "targetSocPct", 80, "priority", 1,
                    "arrival", window[0].toString(), "departure", window[1].toString()));
        }
        long minutes = (window[1].getEpochSecond() - window[0].getEpochSecond()) / 60;
        return Map.of("horizonStart", window[0].toString(), "slotMinutes", SLOT_MINUTES,
                "slotCount", (int) (minutes / SLOT_MINUTES), "algorithm", algorithm, "vehicles", needs);
    }

    static PlanSummary summarise(String algorithm, Instant[] window, Map<String, String> names, JsonNode p) {
        JsonNode m = p.path("metrics");
        List<String> short_ = new ArrayList<>();
        p.path("schedule").path("unmet").forEach(u -> short_.add(
                names.getOrDefault(u.path("vehicleId").asText(), u.path("vehicleId").asText()) + ": " + u.path("reason").asText()));
        return new PlanSummary(algorithm, window[0] + " to " + window[1], m.path("vehiclesNeedingCharge").asInt(),
                m.path("deadlinesMet").asInt(), m.path("cost").path("totalCost").asDouble(),
                m.path("cost").path("averagePricePerKwh").asDouble(), p.path("violations").size(),
                p.path("schedule").path("sessions").size(), short_);
    }

    private static String label(JsonNode v) {
        String vin = v.path("vin").asText();
        return v.path("model").asText() + " ..." + (vin.length() > 4 ? vin.substring(vin.length() - 4) : vin);
    }
}
