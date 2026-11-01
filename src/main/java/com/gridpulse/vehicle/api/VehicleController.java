package com.gridpulse.vehicle.api;

import com.gridpulse.shared.domain.ConnectorType;
import com.gridpulse.shared.domain.EnergyKwh;
import com.gridpulse.shared.domain.PowerKw;
import com.gridpulse.shared.domain.StateOfCharge;
import com.gridpulse.vehicle.Vehicle;
import com.gridpulse.vehicle.VehicleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class VehicleController {
    private final VehicleService service;

    public VehicleController(VehicleService service) {
        this.service = service;
    }

    public record RegisterVehicleRequest(
            @NotBlank @Pattern(regexp = "[A-HJ-NPR-Z0-9]{17}", message = "must be a 17-character VIN") String vin,
            @NotBlank @Size(max = 100) String model,
            @NotNull @Positive @DecimalMax("1000") BigDecimal batteryCapacityKwh,
            @NotNull @Positive @DecimalMax("1000") BigDecimal maxChargeRateKw,
            @NotNull ConnectorType connectorType,
            @Min(0) @Max(100) int stateOfChargePct) {
    }

    public record StateOfChargeRequest(@Min(0) @Max(100) int stateOfChargePct) {
    }

    public record VehicleResponse(UUID id, UUID fleetId, String vin, String model, BigDecimal batteryCapacityKwh,
                                  BigDecimal maxChargeRateKw, ConnectorType connectorType, int stateOfChargePct) {
        static VehicleResponse of(Vehicle v) {
            return new VehicleResponse(v.getId(), v.getFleetId(), v.getVin(), v.getModel(),
                    v.getBatteryCapacity().value(), v.getMaxChargeRate().value(), v.getConnectorType(),
                    v.getStateOfCharge().percent());
        }
    }

    @GetMapping("/fleets/{fleetId}/vehicles")
    public List<VehicleResponse> list(@PathVariable UUID fleetId) {
        return service.listByFleet(fleetId).stream().map(VehicleResponse::of).toList();
    }

    @PostMapping("/fleets/{fleetId}/vehicles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','FLEET_MANAGER')")
    public VehicleResponse register(@PathVariable UUID fleetId, @Valid @RequestBody RegisterVehicleRequest r) {
        return VehicleResponse.of(service.register(fleetId, r.vin(), r.model(), new EnergyKwh(r.batteryCapacityKwh()),
                new PowerKw(r.maxChargeRateKw()), r.connectorType(), new StateOfCharge(r.stateOfChargePct())));
    }

    @GetMapping("/vehicles/{id}")
    public VehicleResponse get(@PathVariable UUID id) {
        return VehicleResponse.of(service.get(id));
    }

    @PutMapping("/vehicles/{id}/state-of-charge")
    @PreAuthorize("hasAnyRole('ADMIN','FLEET_MANAGER','OPERATOR')")
    public VehicleResponse updateSoc(@PathVariable UUID id, @Valid @RequestBody StateOfChargeRequest r) {
        return VehicleResponse.of(service.updateStateOfCharge(id, new StateOfCharge(r.stateOfChargePct())));
    }
}
