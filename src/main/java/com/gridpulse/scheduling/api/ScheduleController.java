package com.gridpulse.scheduling.api;

import com.gridpulse.identity.CurrentUser;
import com.gridpulse.scheduling.SchedulePreviewService;
import com.gridpulse.scheduling.SchedulePreviewService.Algorithm;
import com.gridpulse.scheduling.SchedulePreviewService.Preview;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
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

    public record PreviewRequest(@NotNull Instant horizonStart, @Min(5) @Max(60) int slotMinutes,
                                 @Min(1) @Max(288) int slotCount, @NotNull Algorithm algorithm,
                                 @NotEmpty @Size(max = 5000) List<@Valid VehicleNeedRequest> vehicles) {
    }

    /** Generates a schedule from current tenant data. Nothing is reserved; see POST /api/v1/sessions to book. */
    @PostMapping("/preview")
    @PreAuthorize("hasAnyRole('ADMIN','FLEET_MANAGER','OPERATOR')")
    public Preview preview(@Valid @RequestBody PreviewRequest r) {
        return service.preview(CurrentUser.get().tenantId(), r.horizonStart(), r.slotMinutes(), r.slotCount(),
                r.algorithm(), r.vehicles().stream()
                        .map(v -> new SchedulePreviewService.VehicleNeed(v.vehicleId(), v.targetSocPct(),
                                v.arrival(), v.departure(), v.priority()))
                        .toList());
    }
}
