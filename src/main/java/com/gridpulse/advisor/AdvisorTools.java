package com.gridpulse.advisor;

import com.gridpulse.scheduling.PriorityOutcome;
import com.gridpulse.scheduling.SchedulePreviewService;
import com.gridpulse.scheduling.SchedulePreviewService.Algorithm;
import com.gridpulse.scheduling.SchedulePreviewService.Preview;
import com.gridpulse.scheduling.SchedulePreviewService.PreviewInput;
import com.gridpulse.scheduling.SchedulePreviewService.VehicleNeed;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Tools the language model may call while explaining a schedule. Safety properties:
 * <ul>
 *   <li>One instance per request, bound to the caller's tenant and plan; the model cannot choose a tenant.</li>
 *   <li>Read-only: every tool re-runs the deterministic Java scheduler and validator. Nothing is reserved or saved.</li>
 *   <li>A proposal is only reported as feasible when the validator finds zero hard-constraint violations.</li>
 *   <li>Arguments are validated; unknown vehicles or algorithms are rejected, not guessed.</li>
 *   <li>Calls are capped per request and recorded so the answer can show exactly what was evaluated.</li>
 * </ul>
 */
public final class AdvisorTools {
    static final int MAX_CALLS = 8;

    private final SchedulePreviewService scheduler;
    private final UUID tenantId;
    private final PreviewInput plan;
    private final List<String> calls = Collections.synchronizedList(new ArrayList<>());

    AdvisorTools(SchedulePreviewService scheduler, UUID tenantId, PreviewInput plan) {
        this.scheduler = scheduler;
        this.tenantId = tenantId;
        this.plan = plan;
    }

    /** Compact, model-friendly summary of a deterministic schedule evaluation. */
    public record Evaluation(String algorithm, String change, int vehiclesNeedingCharge, int deadlinesMet,
                             double deadlineSuccessRate, List<PriorityOutcome> byPriority, BigDecimal estimatedCost,
                             BigDecimal averagePricePerKwh, int hardConstraintViolations, boolean feasible,
                             List<String> unmetVehicles, String note) {
    }

    public List<String> calls() {
        return List.copyOf(calls);
    }

    @Tool(description = "Evaluate the current plan with a different scheduling algorithm. "
            + "Allowed values: BASELINE, PRIORITY_SLACK (deadlines first), PRIORITY_SLACK_COST (cheapest slots first). "
            + "Returns deadline, per-priority and cost results computed by the deterministic engine. Nothing is booked.")
    public Evaluation evaluateAlgorithm(@ToolParam(description = "BASELINE, PRIORITY_SLACK or PRIORITY_SLACK_COST") String algorithm) {
        Algorithm a = parse(algorithm);
        return run(new PreviewInput(plan.horizonStart(), plan.slotMinutes(), plan.slotCount(), a, plan.needs()),
                "algorithm=" + a);
    }

    @Tool(description = "Evaluate the current plan if one vehicle's priority were changed (0 = lowest, 10 = highest). "
            + "Returns the deterministic engine's results; nothing is changed or saved.")
    public Evaluation evaluatePriorityChange(@ToolParam(description = "vehicle id from the plan") String vehicleId,
                                             @ToolParam(description = "new priority, 0 to 10") int newPriority) {
        if (newPriority < 0 || newPriority > 10) throw new IllegalArgumentException("priority must be 0..10");
        UUID id;
        try {
            id = UUID.fromString(vehicleId.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown vehicle id: " + vehicleId);
        }
        boolean found = false;
        List<VehicleNeed> needs = new ArrayList<>();
        for (VehicleNeed n : plan.needs()) {
            if (n.vehicleId().equals(id)) {
                needs.add(new VehicleNeed(n.vehicleId(), n.targetSocPct(), n.arrival(), n.departure(), newPriority));
                found = true;
            } else {
                needs.add(n);
            }
        }
        if (!found) throw new IllegalArgumentException("vehicle " + vehicleId + " is not part of this plan");
        return run(new PreviewInput(plan.horizonStart(), plan.slotMinutes(), plan.slotCount(), plan.algorithm(), needs),
                "priority(" + vehicleId + ")=" + newPriority);
    }

    private Evaluation run(PreviewInput input, String change) {
        if (calls.size() >= MAX_CALLS) throw new IllegalStateException("tool call limit reached for this request");
        Preview p = scheduler.preview(tenantId, input);
        boolean feasible = p.violations().isEmpty();
        calls.add(change + " -> met " + p.metrics().deadlinesMet() + "/" + p.metrics().vehiclesNeedingCharge()
                + ", cost " + p.metrics().cost().totalCost() + ", violations " + p.violations().size());
        return new Evaluation(input.algorithm().name(), change, p.metrics().vehiclesNeedingCharge(), p.metrics().deadlinesMet(),
                p.metrics().deadlineSuccessRate(), p.metrics().byPriority(), p.metrics().cost().totalCost(),
                p.metrics().cost().averagePricePerKwh(), p.violations().size(), feasible,
                p.schedule().unmet().stream().map(u -> u.vehicleId() + " (" + u.reason() + ")").limit(20).toList(),
                feasible ? "Validated by the deterministic engine: no hard-constraint violations. Not applied."
                        : "REJECTED: violates hard constraints. Do not recommend.");
    }

    private static Algorithm parse(String s) {
        try {
            return Algorithm.valueOf(s.strip().toUpperCase());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("unknown algorithm: " + s);
        }
    }
}
