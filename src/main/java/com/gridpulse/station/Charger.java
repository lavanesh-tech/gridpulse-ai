package com.gridpulse.station;

import com.gridpulse.shared.domain.ConnectorType;
import com.gridpulse.shared.domain.PowerKw;
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

@Entity
@Table(name = "charger")
public class Charger {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "station_id", nullable = false)
    private UUID stationId;
    @Column(nullable = false)
    private String label;
    @Enumerated(EnumType.STRING)
    @Column(name = "connector_type", nullable = false)
    private ConnectorType connectorType;
    @Column(name = "max_power_kw", nullable = false, precision = 10, scale = 3)
    private BigDecimal maxPowerKw;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChargerStatus status;
    @Version
    private Long version;

    protected Charger() {
    }

    public Charger(UUID tenantId, UUID stationId, String label, ConnectorType connectorType, PowerKw maxPower) {
        this.id = UUID.randomUUID();
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.stationId = Objects.requireNonNull(stationId, "stationId");
        if (label == null || label.isBlank()) throw new IllegalArgumentException("label required");
        this.label = label.strip();
        this.connectorType = Objects.requireNonNull(connectorType, "connectorType");
        if (maxPower.compareTo(PowerKw.ZERO) <= 0) throw new IllegalArgumentException("max power must be > 0");
        this.maxPowerKw = maxPower.value();
        this.status = ChargerStatus.AVAILABLE;
    }

    public boolean isCompatibleWith(ConnectorType vehicleConnector) {
        return connectorType == vehicleConnector;
    }

    public void takeOutOfService() { this.status = ChargerStatus.OUT_OF_SERVICE; }
    public void returnToService() { this.status = ChargerStatus.AVAILABLE; }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getStationId() { return stationId; }
    public String getLabel() { return label; }
    public ConnectorType getConnectorType() { return connectorType; }
    public PowerKw getMaxPower() { return new PowerKw(maxPowerKw); }
    public ChargerStatus getStatus() { return status; }
}
