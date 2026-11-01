package com.gridpulse.station;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "charging_station")
public class ChargingStation {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "grid_connection_id", nullable = false)
    private UUID gridConnectionId;
    @Column(nullable = false)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StationStatus status;
    @Version
    private Long version;

    protected ChargingStation() {
    }

    public ChargingStation(UUID tenantId, UUID gridConnectionId, String name) {
        this.id = UUID.randomUUID();
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.gridConnectionId = Objects.requireNonNull(gridConnectionId, "gridConnectionId");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name required");
        this.name = name.strip();
        this.status = StationStatus.ONLINE;
    }

    public void reportOutage() { this.status = StationStatus.OFFLINE; }
    public void restore() { this.status = StationStatus.ONLINE; }
    public boolean isOnline() { return status == StationStatus.ONLINE; }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getGridConnectionId() { return gridConnectionId; }
    public String getName() { return name; }
    public StationStatus getStatus() { return status; }
}
