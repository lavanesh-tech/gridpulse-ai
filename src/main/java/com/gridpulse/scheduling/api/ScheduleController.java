package com.gridpulse.scheduling.api;

import com.gridpulse.identity.CurrentUser;
import com.gridpulse.scheduling.SchedulePreviewService;
import com.gridpulse.scheduling.SchedulePreviewService.Algorithm;
import com.gridpulse.scheduling.SchedulePreviewService.Preview;
import com.gridpulse.scheduling.SchedulePreviewService.PreviewInput;
import com.gridpulse.scheduling.SchedulePreviewService.Recalculation;
import com.gridpulse.shared.domain.PowerKw;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/schedules")
public class ScheduleController {
    private final SchedulePreviewService service;

    public ScheduleController(SchedulePreviewService service) {
        this.service = service;
    }

    public record VehicleNeedRequest(@NotNull UUID vehicleId, @Min(0) @Max(100) int targetSocPct,
                                     @NotNull Instant arrival, @NotNull Instant departure,
                                     @Min(0) @Max(10) int priority) {
    }

    /**
     * {@code algorithm}: BASELINE (first-come, full power), PRIORITY_SLACK (deadlines first),
     * PRIORITY_SLACK_COST (cheapest tariff slots first).
     */
    public record PreviewRequest(@NotNull Instant horizonStart, @Min(5) @Max(60) int slotMinutes,
                                 @Min(1) @Max(288) int slotCount, @NotNull Algorithm algorithm,
                                 @NotEmpty @Size(max = 5000) List<@Valid VehicleNeedRequest> vehicles) {
        PreviewInput toInput() {
            return new PreviewInput(horizonStart, slotMinutes, slotCount, algorithm, vehicles.stream()
                    .map(v -> new SchedulePreviewService.VehicleNeed(v.vehicleId(), v.targetSocPct(), v.arrival(),
                            v.departure(), v.priority()))
                    .toList());
        }
    }

    public record DisruptionRequest(@NotNull Instant at, @Size(max = 500) Set<UUID> chargersOutOfService,
                                    @Size(max = 100) Map<UUID, @NotNull @PositiveOrZero @DecimalMax("100000") BigDecimal> gridCapacityKw) {
    }

    public record RecalculateRequest(@NotNull @Valid PreviewRequest plan, @NotNull @Valid DisruptionRequest disruption) {
    }

    /** Generates a schedule from current tenant data, around existing reservations. Nothing is reserved. */
    @PostMapping("/preview")
    @PreAuthorize("hasAnyRole('ADMIN','FLEET_MANAGER','OPERATOR')")
    public Preview preview(@Valid @RequestBody PreviewRequest r) {
        return service.preview(CurrentUser.get().tenantId(), r.toInput());
    }

    /** What-if: plan, then simulate a station outage and/or grid curtailment at a point in time and re-plan. */
    @PostMapping("/recalculate")
    @PreAuthorize("hasAnyRole('ADMIN','FLEET_MANAGER','OPERATOR')")
    public Recalculation recalculate(@Valid @RequestBody RecalculateRequest r) {
        Map<UUID, PowerKw> overrides = new HashMap<>();
        if (r.disruption().gridCapacityKw() != null) {
            r.disruption().gridCapacityKw().forEach((g, kw) -> overrides.put(g, new PowerKw(kw)));
        }
        Set<UUID> out = r.disruption().chargersOutOfService() == null ? Set.of() : r.disruption().chargersOutOfService();
        return service.recalculate(CurrentUser.get().tenantId(), r.plan().toInput(), r.disruption().at(), out, overrides);
    }
}
