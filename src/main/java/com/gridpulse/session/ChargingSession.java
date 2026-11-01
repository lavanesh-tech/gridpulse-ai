package com.gridpulse.session;

import com.gridpulse.shared.domain.PowerKw;
import com.gridpulse.shared.domain.TimeWindow;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A reserved or running charging session. Overlaps are prevented by PostgreSQL exclusion
 * constraints (see V2 migration), so even concurrent requests cannot double-book.
 */
@Entity
@Table(name = "charging_session")
public class ChargingSession {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;
    @Column(name = "charger_id", nullable = false)
    private UUID chargerId;
    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;
    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;
    @Column(name = "power_kw", nullable = false, precision = 10, scale = 3)
    private BigDecimal powerKw;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status;
    @Column(name = "interruption_reason")
    private String interruptionReason;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Version
    private Long version;

    protected ChargingSession() {
    }

    public ChargingSession(UUID tenantId, UUID vehicleId, UUID chargerId, TimeWindow window, PowerKw power) {
        this.id = UUID.randomUUID();
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.vehicleId = Objects.requireNonNull(vehicleId, "vehicleId");
        this.chargerId = Objects.requireNonNull(chargerId, "chargerId");
        Objects.requireNonNull(window, "window");
        this.startsAt = window.start();
        this.endsAt = window.end();
        if (power.compareTo(PowerKw.ZERO) <= 0) throw new IllegalArgumentException("power must be > 0");
        this.powerKw = power.value();
        this.status = SessionStatus.RESERVED;
        this.createdAt = Instant.now();
    }

    public void start() {
        require(SessionStatus.RESERVED, "start");
        status = SessionStatus.ACTIVE;
    }

    public void complete() {
        require(SessionStatus.ACTIVE, "complete");
        status = SessionStatus.COMPLETED;
    }

    public void interrupt(String reason) {
        require(SessionStatus.ACTIVE, "interrupt");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("interruption reason required");
        status = SessionStatus.INTERRUPTED;
        interruptionReason = reason.strip();
    }

    public void cancel() {
        require(SessionStatus.RESERVED, "cancel");
        status = SessionStatus.CANCELLED;
    }

    private void require(SessionStatus expected, String action) {
        if (status != expected) {
            throw new IllegalStateException("cannot " + action + " a session that is " + status);
        }
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getVehicleId() { return vehicleId; }
    public UUID getChargerId() { return chargerId; }
    public TimeWindow getWindow() { return new TimeWindow(startsAt, endsAt); }
    public PowerKw getPower() { return new PowerKw(powerKw); }
    public SessionStatus getStatus() { return status; }
    public String getInterruptionReason() { return interruptionReason; }
}
