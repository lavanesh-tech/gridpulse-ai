package com.gridpulse.grid;

import com.gridpulse.shared.domain.PowerKw;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "grid_connection")
public class GridConnection {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(nullable = false)
    private String name;
    @Column(name = "capacity_limit_kw", nullable = false, precision = 10, scale = 3)
    private BigDecimal capacityLimitKw;
    @Column(name = "time_zone", nullable = false)
    private String timeZone;
    @Version
    private Long version;

    protected GridConnection() {
    }

    public GridConnection(UUID tenantId, String name, PowerKw capacityLimit) {
        this(tenantId, name, capacityLimit, ZoneId.of("UTC"));
    }

    public GridConnection(UUID tenantId, String name, PowerKw capacityLimit, ZoneId timeZone) {
        this.timeZone = Objects.requireNonNull(timeZone, "timeZone").getId();
        this.id = UUID.randomUUID();
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name required");
        this.name = name.strip();
        changeCapacity(capacityLimit);
    }

    public void changeCapacity(PowerKw capacityLimit) {
        if (capacityLimit.compareTo(PowerKw.ZERO) <= 0) throw new IllegalArgumentException("capacity must be > 0");
        this.capacityLimitKw = capacityLimit.value();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getName() { return name; }
    public PowerKw getCapacityLimit() { return new PowerKw(capacityLimitKw); }
    public ZoneId getTimeZone() { return ZoneId.of(timeZone); }
}
