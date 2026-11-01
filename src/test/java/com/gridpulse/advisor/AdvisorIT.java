package com.gridpulse.advisor;

import com.gridpulse.PostgresIntegrationTest;
import com.gridpulse.fleet.Fleet;
import com.gridpulse.fleet.FleetRepository;
import com.gridpulse.grid.GridConnection;
import com.gridpulse.grid.GridConnectionRepository;
import com.gridpulse.scheduling.SchedulePreviewService;
import com.gridpulse.scheduling.SchedulePreviewService.Algorithm;
import com.gridpulse.scheduling.SchedulePreviewService.PreviewInput;
import com.gridpulse.scheduling.SchedulePreviewService.VehicleNeed;
import com.gridpulse.shared.domain.ConnectorType;
import com.gridpulse.shared.domain.EnergyKwh;
import com.gridpulse.shared.domain.PowerKw;
import com.gridpulse.shared.domain.StateOfCharge;
import com.gridpulse.shared.error.NotFoundException;
import com.gridpulse.station.Charger;
import com.gridpulse.station.ChargerRepository;
import com.gridpulse.station.ChargingStation;
import com.gridpulse.station.ChargingStationRepository;
import com.gridpulse.vehicle.Vehicle;
import com.gridpulse.vehicle.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RAG + advisor against real pgvector, with a fake chat model (no network, no cost) and the offline embedder. */
@SpringBootTest
@Import(AdvisorIT.FakeChat.class)
class AdvisorIT extends PostgresIntegrationTest {

    @TestConfiguration
    static class FakeChat {
        static final List<String> PROMPTS = new CopyOnWriteArrayList<>();

        @Bean
        ChatModel fakeChatModel() {
            return new ChatModel() {
                @Override
                public ChatResponse call(Prompt prompt) {
                    PROMPTS.add(prompt.getInstructions().stream().map(m -> m.getText()).collect(Collectors.joining("\n")));
                    return new ChatResponse(List.of(new Generation(new AssistantMessage(
                            "Peak electricity costs 0.32 per kWh [S1]. This claim cites a source that was never given [S9]."))));
                }

                @Override
                public ChatOptions getDefaultOptions() {
                    return ToolCallingChatOptions.builder().build();
                }
            };
        }
    }

    @Autowired KnowledgeService knowledge;
    @Autowired EnergyAdvisorService advisor;
    @Autowired SchedulePreviewService previews;
    @Autowired JdbcTemplate jdbc;
    @Autowired FleetRepository fleets;
    @Autowired VehicleRepository vehicles;
    @Autowired GridConnectionRepository grids;
    @Autowired ChargingStationRepository stations;
    @Autowired ChargerRepository chargers;

    UUID tenantA;
    UUID tenantB;

    @BeforeEach
    void setUp() {
        tenantA = tenant("A");
        tenantB = tenant("B");
        knowledge.loadSamples(tenantA, "admin-a");
        knowledge.ingest(tenantB, "admin-b", "Tenant B private tariff", null, DocType.TARIFF,
                "# Tenant B private tariff\n\nThe peak electricity price for tenant B is 0.99 per kWh. Confidential.");
    }

    private UUID tenant(String label) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO tenant (id, name) VALUES (?, ?)", id, label + "-" + id);
        return id;
    }

    @Test
    void answersOnlyFromTheCallersTenantAndKeepsOnlyValidCitations() {
        int before = FakeChat.PROMPTS.size();
        EnergyAdvisorService.Answer a = advisor.ask(tenantA, "viewer-a", "What is the peak electricity price?", null);

        assertThat(a.aiAvailable()).isTrue();
        assertThat(a.sources()).isNotEmpty()
                .allSatisfy(s -> assertThat(s.title()).endsWith("(synthetic sample)"));
        assertThat(a.sources().get(0).title()).isEqualTo("Time-of-Use Tariff Guide (synthetic sample)");
        String prompt = FakeChat.PROMPTS.get(before);
        assertThat(prompt).contains("[S1]").contains("0.32").doesNotContain("0.99").doesNotContain("Confidential");
        assertThat(a.citations()).extracting(EnergyAdvisorService.Citation::ref).containsExactly("S1");
        assertThat(a.grounded()).isTrue();
        assertThat(a.embeddingModel()).isEqualTo(LexicalEmbeddingProvider.NAME);
    }

    @Test
    void refusesWithoutCallingTheModelWhenNothingRelevantIsRetrieved() {
        int before = FakeChat.PROMPTS.size();
        EnergyAdvisorService.Answer a = advisor.ask(tenantA, "viewer-a", "Who won the football world cup?", null);
        assertThat(a.sources()).isEmpty();
        assertThat(a.answer()).isEqualTo("The available documents do not cover this.");
        assertThat(a.grounded()).isFalse();
        assertThat(FakeChat.PROMPTS).hasSize(before);
    }

    @Test
    void documentTypeFilterRestrictsSources() {
        EnergyAdvisorService.Answer a = advisor.ask(tenantA, "viewer-a", "What is the grid capacity limit?", DocType.GRID);
        assertThat(a.sources()).isNotEmpty()
                .allSatisfy(s -> assertThat(s.title()).isEqualTo("Grid Connection Agreement Summary (synthetic sample)"));
    }

    @Test
    void samplesAreIdempotentAndDeletionIsTenantScoped() {
        List<KnowledgeService.IngestResult> again = knowledge.loadSamples(tenantA, "admin-a");
        assertThat(again).hasSize(4).allSatisfy(r -> assertThat(r.duplicate()).isTrue());
        List<KnowledgeStore.DocumentRow> docs = knowledge.list(tenantA);
        assertThat(docs).hasSize(4).allSatisfy(d -> assertThat(d.chunks()).isGreaterThan(0));

        UUID docOfA = docs.get(0).id();
        assertThatThrownBy(() -> knowledge.delete(tenantB, "admin-b", docOfA)).isInstanceOf(NotFoundException.class);
        knowledge.delete(tenantA, "admin-a", docOfA);
        assertThat(knowledge.list(tenantA)).hasSize(3);
    }

    @Test
    void retrievalEvaluationOnTheSampleSet() {
        List<KnowledgeService.EvalCase> cases = knowledge.sampleEvalCases();
        assertThat(cases).hasSize(14);
        RetrievalEvaluation e = knowledge.evaluate(tenantA, cases, 3);
        // Measured offline with the same chunker and embedder: hit@3 = 0.9286, MRR = 0.881.
        // The 10 keyword questions rank first; the lexical embedder misses one paraphrase entirely.
        assertThat(e.questions()).isEqualTo(14);
        assertThat(e.cases().subList(0, 10)).allSatisfy(c -> assertThat(c.rank()).as(c.question()).isEqualTo(1));
        assertThat(e.hitAtK()).isGreaterThanOrEqualTo(0.85);
        assertThat(e.cases()).allSatisfy(c -> assertThat(c.retrievedDocuments())
                .allSatisfy(t -> assertThat(t).doesNotContain("Tenant B")));
    }

    private PreviewInput planForNewDepot() {
        UUID t = tenant("C");
        Fleet fleet = fleets.save(new Fleet(t, "Vans"));
        GridConnection grid = grids.save(new GridConnection(t, "Feed", PowerKw.of(150)));
        ChargingStation station = stations.save(new ChargingStation(t, grid.getId(), "Depot"));
        chargers.save(new Charger(t, station.getId(), "C1", ConnectorType.CCS1, PowerKw.of(150)));
        chargers.save(new Charger(t, station.getId(), "C2", ConnectorType.CCS1, PowerKw.of(150)));
        Instant start = Instant.parse("2026-01-06T00:00:00Z");
        List<VehicleNeed> needs = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Vehicle v = vehicles.save(new Vehicle(t, fleet.getId(), ("1HGCM82633A00000" + i), "Van", EnergyKwh.of(100),
                    PowerKw.of(150), ConnectorType.CCS1, new StateOfCharge(20)));
            needs.add(new VehicleNeed(v.getId(), 80, start, start.plusSeconds(6 * 3600), i));
        }
        lastTenant = t;
        return new PreviewInput(start, 15, 32, Algorithm.PRIORITY_SLACK, needs);
    }

    UUID lastTenant;

    @Test
    void toolsAreReadOnlyValidatedTenantBoundAndCapped() {
        PreviewInput plan = planForNewDepot();
        AdvisorTools tools = new AdvisorTools(previews, lastTenant, plan);

        AdvisorTools.Evaluation cost = tools.evaluateAlgorithm("priority_slack_cost");
        assertThat(cost.feasible()).isTrue();
        assertThat(cost.hardConstraintViolations()).isZero();
        assertThat(cost.note()).contains("Not applied");

        AdvisorTools.Evaluation promoted = tools.evaluatePriorityChange(plan.needs().get(0).vehicleId().toString(), 9);
        assertThat(promoted.feasible()).isTrue();

        assertThatThrownBy(() -> tools.evaluateAlgorithm("DROP TABLE vehicle")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tools.evaluatePriorityChange(UUID.randomUUID().toString(), 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tools.evaluatePriorityChange("not-a-uuid", 5)).isInstanceOf(IllegalArgumentException.class);

        for (int i = tools.calls().size(); i < AdvisorTools.MAX_CALLS; i++) tools.evaluateAlgorithm("BASELINE");
        assertThatThrownBy(() -> tools.evaluateAlgorithm("BASELINE")).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM charging_session WHERE tenant_id = ?", Integer.class, lastTenant))
                .isZero();
    }

    @Test
    void explanationReturnsEngineFactsComparisonAndNarrative() {
        PreviewInput plan = planForNewDepot();
        EnergyAdvisorService.Explanation e = advisor.explainSchedule(lastTenant, "manager", plan);
        assertThat(e.facts().vehiclesNeedingCharge()).isEqualTo(3);
        assertThat(e.facts().hardConstraintViolations()).isZero();
        assertThat(e.comparison()).extracting(EnergyAdvisorService.Comparison::algorithm)
                .containsExactly("BASELINE", "PRIORITY_SLACK", "PRIORITY_SLACK_COST");
        assertThat(e.comparison()).allSatisfy(c -> assertThat(c.hardConstraintViolations()).isZero());
        assertThat(e.aiAvailable()).isTrue();
        assertThat(e.narrative()).isNotBlank();
        assertThat(FakeChat.PROMPTS.get(FakeChat.PROMPTS.size() - 1)).contains("Facts JSON").contains("vehiclesNeedingCharge");
    }
}
