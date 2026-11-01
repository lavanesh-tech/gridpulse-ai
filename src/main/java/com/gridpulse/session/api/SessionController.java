package com.gridpulse.session.api;

import com.gridpulse.identity.CurrentUser;
import com.gridpulse.session.ChargingSession;
import com.gridpulse.session.ChargingSessionService;
import com.gridpulse.session.SessionStatus;
import com.gridpulse.shared.domain.PowerKw;
import com.gridpulse.shared.domain.TimeWindow;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {
    private final ChargingSessionService service;

    public SessionController(ChargingSessionService service) {
        this.service = service;
    }

    public record ReserveRequest(@NotNull UUID vehicleId, @NotNull UUID chargerId,
                                 @NotNull Instant startsAt, @NotNull Instant endsAt,
                                 @NotNull @Positive @DecimalMax("1000") BigDecimal powerKw) {
    }

    public record InterruptRequest(@NotBlank @Size(max = 500) String reason) {
    }

    public record SessionResponse(UUID id, UUID vehicleId, UUID chargerId, Instant startsAt, Instant endsAt,
                                  BigDecimal powerKw, SessionStatus status, String interruptionReason) {
        static SessionResponse of(ChargingSession s) {
            return new SessionResponse(s.getId(), s.getVehicleId(), s.getChargerId(), s.getWindow().start(),
                    s.getWindow().end(), s.getPower().value(), s.getStatus(), s.getInterruptionReason());
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','FLEET_MANAGER','OPERATOR')")
    public SessionResponse reserve(@Valid @RequestBody ReserveRequest r) {
        CurrentUser u = CurrentUser.get();
        return SessionResponse.of(service.reserve(u.tenantId(), u.username(), r.vehicleId(), r.chargerId(),
                new TimeWindow(r.startsAt(), r.endsAt()), new PowerKw(r.powerKw())));
    }

    @GetMapping
    public List<SessionResponse> forCharger(@RequestParam UUID chargerId) {
        return service.forCharger(CurrentUser.get().tenantId(), chargerId).stream().map(SessionResponse::of).toList();
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','FLEET_MANAGER','OPERATOR')")
    public SessionResponse cancel(@PathVariable UUID id) {
        CurrentUser u = CurrentUser.get();
        return SessionResponse.of(service.cancel(u.tenantId(), u.username(), id));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public SessionResponse start(@PathVariable UUID id) {
        CurrentUser u = CurrentUser.get();
        return SessionResponse.of(service.start(u.tenantId(), u.username(), id));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public SessionResponse complete(@PathVariable UUID id) {
        CurrentUser u = CurrentUser.get();
        return SessionResponse.of(service.complete(u.tenantId(), u.username(), id));
    }

    @PostMapping("/{id}/interrupt")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public SessionResponse interrupt(@PathVariable UUID id, @Valid @RequestBody InterruptRequest r) {
        CurrentUser u = CurrentUser.get();
        return SessionResponse.of(service.interrupt(u.tenantId(), u.username(), id, r.reason()));
    }
}
