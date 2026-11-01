package com.gridpulse.station.api;

import com.gridpulse.shared.domain.ConnectorType;
import com.gridpulse.shared.domain.PowerKw;
import com.gridpulse.station.Charger;
import com.gridpulse.station.ChargerStatus;
import com.gridpulse.station.ChargingStation;
import com.gridpulse.station.StationService;
import com.gridpulse.station.StationStatus;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stations")
public class StationController {
    private final StationService service;

    public StationController(StationService service) {
        this.service = service;
    }

    public record CreateStationRequest(@NotNull UUID gridConnectionId, @NotBlank @Size(max = 200) String name) {
    }

    public record AddChargerRequest(@NotBlank @Size(max = 50) String label, @NotNull ConnectorType connectorType,
                                    @NotNull @Positive @DecimalMax("1000") BigDecimal maxPowerKw) {
    }

    public record StationResponse(UUID id, UUID gridConnectionId, String name, StationStatus status) {
        static StationResponse of(ChargingStation s) {
            return new StationResponse(s.getId(), s.getGridConnectionId(), s.getName(), s.getStatus());
        }
    }

    public record ChargerResponse(UUID id, UUID stationId, String label, ConnectorType connectorType,
                                  BigDecimal maxPowerKw, ChargerStatus status) {
        static ChargerResponse of(Charger c) {
            return new ChargerResponse(c.getId(), c.getStationId(), c.getLabel(), c.getConnectorType(),
                    c.getMaxPower().value(), c.getStatus());
        }
    }

    @GetMapping
    public List<StationResponse> list() {
        return service.list().stream().map(StationResponse::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public StationResponse create(@Valid @RequestBody CreateStationRequest r) {
        return StationResponse.of(service.create(r.gridConnectionId(), r.name()));
    }

    @GetMapping("/{id}/chargers")
    public List<ChargerResponse> chargers(@PathVariable UUID id) {
        return service.chargers(id).stream().map(ChargerResponse::of).toList();
    }

    @PostMapping("/{id}/chargers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ChargerResponse addCharger(@PathVariable UUID id, @Valid @RequestBody AddChargerRequest r) {
        return ChargerResponse.of(service.addCharger(id, r.label(), r.connectorType(), new PowerKw(r.maxPowerKw())));
    }

    @PostMapping("/{id}/outage")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public StationResponse outage(@PathVariable UUID id) {
        return StationResponse.of(service.reportOutage(id));
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public StationResponse restore(@PathVariable UUID id) {
        return StationResponse.of(service.restore(id));
    }
}
