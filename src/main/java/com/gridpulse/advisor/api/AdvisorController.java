package com.gridpulse.advisor.api;

import com.gridpulse.advisor.DocType;
import com.gridpulse.advisor.EnergyAdvisorService;
import com.gridpulse.advisor.KnowledgeService;
import com.gridpulse.advisor.KnowledgeStore;
import com.gridpulse.advisor.RetrievalEvaluation;
import com.gridpulse.identity.CurrentUser;
import com.gridpulse.scheduling.SchedulePreviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/advisor")
public class AdvisorController {
    private final KnowledgeService knowledge;
    private final EnergyAdvisorService advisor;

    public AdvisorController(KnowledgeService knowledge, EnergyAdvisorService advisor) {
        this.knowledge = knowledge;
        this.advisor = advisor;
    }

    public record IngestRequest(@NotBlank @Size(max = 300) String title, @Size(max = 500) String source,
                                @NotNull DocType docType, @NotBlank @Size(max = 200_000) String content) {
    }

    public record AskRequest(@NotBlank @Size(max = 2000) String question, DocType docType) {
    }

    public record EvalCaseDto(@NotBlank @Size(max = 2000) String question, @NotBlank @Size(max = 300) String expectedDocumentTitle) {
    }

    public record EvaluateRequest(@Size(max = 200) List<@Valid EvalCaseDto> cases, @Min(1) @Max(20) Integer k) {
    }

    public record VehicleNeedDto(@NotNull UUID vehicleId, @Min(0) @Max(100) int targetSocPct,
                                 @NotNull Instant arrival, @NotNull Instant departure, @Min(0) @Max(10) int priority) {
    }

    public record ExplainRequest(@NotNull Instant horizonStart, @Min(5) @Max(60) int slotMinutes,
                                 @Min(1) @Max(288) int slotCount, @NotNull SchedulePreviewService.Algorithm algorithm,
                                 @NotEmpty @Size(max = 2000) List<@Valid VehicleNeedDto> vehicles) {
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("chatModelConfigured", advisor.aiAvailable(), "embeddingModel", knowledge.embeddingModel());
    }

    @PostMapping("/documents")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public KnowledgeService.IngestResult ingest(@Valid @RequestBody IngestRequest r) {
        CurrentUser u = CurrentUser.get();
        return knowledge.ingest(u.tenantId(), u.username(), r.title(), r.source(), r.docType(), r.content());
    }

    @PostMapping("/documents/samples")
    @PreAuthorize("hasRole('ADMIN')")
    public List<KnowledgeService.IngestResult> loadSamples() {
        CurrentUser u = CurrentUser.get();
        return knowledge.loadSamples(u.tenantId(), u.username());
    }

    @GetMapping("/documents")
    public List<KnowledgeStore.DocumentRow> documents() {
        return knowledge.list(CurrentUser.get().tenantId());
    }

    @DeleteMapping("/documents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        CurrentUser u = CurrentUser.get();
        knowledge.delete(u.tenantId(), u.username(), id);
    }

    @PostMapping("/ask")
    public EnergyAdvisorService.Answer ask(@Valid @RequestBody AskRequest r) {
        CurrentUser u = CurrentUser.get();
        return advisor.ask(u.tenantId(), u.username(), r.question(), r.docType());
    }

    @PostMapping("/explain-schedule")
    @PreAuthorize("hasAnyRole('ADMIN','FLEET_MANAGER','OPERATOR')")
    public EnergyAdvisorService.Explanation explain(@Valid @RequestBody ExplainRequest r) {
        CurrentUser u = CurrentUser.get();
        var input = new SchedulePreviewService.PreviewInput(r.horizonStart(), r.slotMinutes(), r.slotCount(), r.algorithm(),
                r.vehicles().stream().map(v -> new SchedulePreviewService.VehicleNeed(v.vehicleId(), v.targetSocPct(),
                        v.arrival(), v.departure(), v.priority())).toList());
        return advisor.explainSchedule(u.tenantId(), u.username(), input);
    }

    /** Retrieval quality: hit@k and MRR over labelled questions (defaults to the bundled sample set). */
    @PostMapping("/evaluate")
    @PreAuthorize("hasRole('ADMIN')")
    public RetrievalEvaluation evaluate(@Valid @RequestBody EvaluateRequest r) {
        List<KnowledgeService.EvalCase> cases = r.cases() == null || r.cases().isEmpty()
                ? knowledge.sampleEvalCases()
                : r.cases().stream().map(c -> new KnowledgeService.EvalCase(c.question(), c.expectedDocumentTitle())).toList();
        return knowledge.evaluate(CurrentUser.get().tenantId(), cases, r.k() == null ? 3 : r.k());
    }
}
