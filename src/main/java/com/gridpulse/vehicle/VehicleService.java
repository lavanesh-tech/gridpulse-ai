package com.gridpulse.vehicle;

import com.gridpulse.audit.AuditLog;
import com.gridpulse.fleet.FleetService;
import com.gridpulse.identity.CurrentUser;
import com.gridpulse.shared.domain.ConnectorType;
import com.gridpulse.shared.domain.EnergyKwh;
import com.gridpulse.shared.domain.PowerKw;
import com.gridpulse.shared.domain.StateOfCharge;
import com.gridpulse.shared.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class VehicleService {
    private final VehicleRepository vehicles;
    private final FleetService fleets;
    private final AuditLog audit;

    public VehicleService(VehicleRepository vehicles, FleetService fleets, AuditLog audit) {
        this.vehicles = vehicles;
        this.fleets = fleets;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Vehicle> listByFleet(UUID fleetId) {
        fleets.get(fleetId); // 404 if the fleet is not the caller's
        return vehicles.findByTenantIdAndFleetId(CurrentUser.get().tenantId(), fleetId);
    }

    @Transactional(readOnly = true)
    public Vehicle get(UUID id) {
        return vehicles.findByIdAndTenantId(id, CurrentUser.get().tenantId())
                .orElseThrow(() -> new NotFoundException("vehicle", id));
    }

    @Transactional
    public Vehicle register(UUID fleetId, String vin, String model, EnergyKwh battery, PowerKw maxRate,
                            ConnectorType connector, StateOfCharge soc) {
        fleets.get(fleetId);
        CurrentUser user = CurrentUser.get();
        Vehicle v = vehicles.saveAndFlush(new Vehicle(user.tenantId(), fleetId, vin, model, battery, maxRate, connector, soc));
        audit.record(user.tenantId(), user.username(), "REGISTER_VEHICLE", "vehicle", v.getId().toString(),
                Map.of("vin", vin, "fleetId", fleetId.toString()));
        return v;
    }

    @Transactional
    public Vehicle updateStateOfCharge(UUID id, StateOfCharge soc) {
        Vehicle v = get(id);
        v.updateStateOfCharge(soc);
        return v;
    }
}
