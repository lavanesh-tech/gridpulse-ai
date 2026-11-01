package com.gridpulse.vehicle;

import com.gridpulse.shared.domain.ConnectorType;
import com.gridpulse.shared.domain.EnergyKwh;
import com.gridpulse.shared.domain.PowerKw;
import com.gridpulse.shared.domain.StateOfCharge;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "vehicle")
public class Vehicle {
    /** 17 characters, letters I, O and Q are never used in VINs. */
    private static final Pattern VIN = Pattern.compile("[A-HJ-NPR-Z0-9]{17}");

    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "fleet_id", nullable = false)
    private UUID fleetId;
    @Column(nullable = false)
    private String vin;
    @Column(nullable = false)
    private String model;
    @Column(name = "battery_capacity_kwh", nullable = false, precision = 10, scale = 3)
    private BigDecimal batteryCapacityKwh;
    @Column(name = "max_charge_rate_kw", nullable = false, precision = 10, scale = 3)
    private BigDecimal maxChargeRateKw;
    @Enumerated(EnumType.STRING)
    @Column(name = "connector_type", nullable = false)
    private ConnectorType connectorType;
    @Column(name = "state_of_charge_pct", nullable = false)
    private short stateOfChargePct;
    @Version
    private Long version;

    protected Vehicle() {
    }

    public Vehicle(UUID tenantId, UUID fleetId, String vin, String model, EnergyKwh batteryCapacity,
                   PowerKw maxChargeRate, ConnectorType connectorType, StateOfCharge stateOfCharge) {
        this.id = UUID.randomUUID();
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.fleetId = Objects.requireNonNull(fleetId, "fleetId");
        if (vin == null || !VIN.matcher(vin).matches()) throw new IllegalArgumentException("invalid VIN");
        this.vin = vin;
        if (model == null || model.isBlank()) throw new IllegalArgumentException("model required");
        this.model = model.strip();
        if (batteryCapacity.compareTo(EnergyKwh.ZERO) <= 0) throw new IllegalArgumentException("battery capacity must be > 0");
        if (maxChargeRate.compareTo(PowerKw.ZERO) <= 0) throw new IllegalArgumentException("charge rate must be > 0");
        this.batteryCapacityKwh = batteryCapacity.value();
        this.maxChargeRateKw = maxChargeRate.value();
        this.connectorType = Objects.requireNonNull(connectorType, "connectorType");
        updateStateOfCharge(stateOfCharge);
    }

    public void updateStateOfCharge(StateOfCharge soc) {
        this.stateOfChargePct = (short) Objects.requireNonNull(soc, "stateOfCharge").percent();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getFleetId() { return fleetId; }
    public String getVin() { return vin; }
    public String getModel() { return model; }
    public EnergyKwh getBatteryCapacity() { return new EnergyKwh(batteryCapacityKwh); }
    public PowerKw getMaxChargeRate() { return new PowerKw(maxChargeRateKw); }
    public ConnectorType getConnectorType() { return connectorType; }
    public StateOfCharge getStateOfCharge() { return new StateOfCharge(stateOfChargePct); }
}
