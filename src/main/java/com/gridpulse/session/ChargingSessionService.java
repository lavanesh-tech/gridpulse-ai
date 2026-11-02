package com.gridpulse.session;

import com.gridpulse.audit.AuditLog;
import com.gridpulse.events.Outbox;
import com.gridpulse.shared.domain.PowerKw;
import com.gridpulse.shared.domain.TimeWindow;
import com.gridpulse.shared.error.BusinessRuleException;
import com.gridpulse.shared.error.NotFoundException;
import com.gridpulse.station.Charger;
import com.gridpulse.station.ChargerRepository;
import com.gridpulse.station.ChargerStatus;
import com.gridpulse.station.ChargingStation;
import com.gridpulse.station.ChargingStationRepository;
import com.gridpulse.vehicle.Vehicle;
import com.gridpulse.vehicle.VehicleRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Reservation rules, all enforced on the server:
 * tenant ownership, connector compatibility, charger/station in service, power within charger and vehicle limits,
 * grid capacity across all live sessions on the same grid connection, and no overlap (DB exclusion constraints).
 */
@Service
public class ChargingSessionService {
    private final ChargingSessionRepository sessions;
    private final VehicleRepository vehicles;
    private final ChargerRepository chargers;
    private final ChargingStationRepository stations;
    private final JdbcTemplate jdbc;
    private final AuditLog audit;
    private final Outbox outbox;

    public ChargingSessionService(ChargingSessionRepository sessions, VehicleRepository vehicles,
                                  ChargerRepository chargers, ChargingStationRepository stations,
                                  JdbcTemplate jdbc, AuditLog audit, Outbox outbox) {
        this.outbox = outbox;
        this.sessions = sessions;
        this.vehicles = vehicles;
        this.chargers = chargers;
        this.stations = stations;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional
    public ChargingSession reserve(UUID tenantId, String actor, UUID vehicleId, UUID chargerId,
                                   TimeWindow window, PowerKw power) {
        Vehicle vehicle = vehicles.findByIdAndTenantId(vehicleId, tenantId)
                .orElseThrow(() -> new NotFoundException("vehicle", vehicleId));
        Charger charger = chargers.findByIdAndTenantId(chargerId, tenantId)
                .orElseThrow(() -> new NotFoundException("charger", chargerId));
        ChargingStation station = stations.findByIdAndTenantId(charger.getStationId(), tenantId)
                .orElseThrow(() -> new NotFoundException("station", charger.getStationId()));

        if (!charger.isCompatibleWith(vehicle.getConnectorType())) {
            throw new BusinessRuleException("charger connector " + charger.getConnectorType()
                    + " is incompatible with vehicle connector " + vehicle.getConnectorType());
        }
        if (charger.getStatus() != ChargerStatus.AVAILABLE || !station.isOnline()) {
            throw new BusinessRuleException("charger is not in service");
        }
        if (power.exceeds(charger.getMaxPower()) || power.exceeds(vehicle.getMaxChargeRate())) {
            throw new BusinessRuleException("requested power exceeds charger or vehicle limit");
        }

        // Pessimistic lock on the grid row serialises reservations that share a grid connection,
        // so two concurrent requests cannot both see spare capacity and together exceed the limit.
        BigDecimal capacity = jdbc.queryForObject(
                "SELECT capacity_limit_kw FROM grid_connection WHERE id = ? AND tenant_id = ? FOR UPDATE",
                BigDecimal.class, station.getGridConnectionId(), tenantId);
        List<GridLoad.Booking> existing = jdbc.query("""
                        SELECT cs.starts_at, cs.ends_at, cs.power_kw
                        FROM charging_session cs
                        JOIN charger c ON c.id = cs.charger_id
                        JOIN charging_station s ON s.id = c.station_id
                        WHERE s.grid_connection_id = ? AND cs.status IN ('RESERVED','ACTIVE')
                          AND cs.starts_at < ? AND cs.ends_at > ?
                        """,
                (rs, i) -> new GridLoad.Booking(rs.getTimestamp("starts_at").toInstant(),
                        rs.getTimestamp("ends_at").toInstant(), rs.getBigDecimal("power_kw")),
                station.getGridConnectionId(), Timestamp.from(window.end()), Timestamp.from(window.start()));
        BigDecimal peak = GridLoad.peakWithin(existing, window);
        if (peak.add(power.value()).compareTo(capacity) > 0) {
            throw new BusinessRuleException("grid capacity exceeded: existing peak " + peak.toPlainString()
                    + " kW + requested " + power.value().toPlainString() + " kW > limit " + capacity.toPlainString() + " kW");
        }

        ChargingSession saved;
        try {
            saved = sessions.saveAndFlush(new ChargingSession(tenantId, vehicleId, chargerId, window, power));
        } catch (DataIntegrityViolationException e) {
            throw new ReservationConflictException(
                    "charger or vehicle already booked during " + window, e);
        }
        audit.record(tenantId, actor, "RESERVE_SESSION", "charging_session", saved.getId().toString(),
                Map.of("vehicleId", vehicleId.toString(), "chargerId", chargerId.toString(),
                        "start", window.start().toString(), "end", window.end().toString()));
        publish(saved, "SESSION_RESERVED");
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<ChargingSession> history(UUID tenantId, Instant from, Instant to, int page, int size) {
        if (!to.isAfter(from)) throw new IllegalArgumentException("'to' must be after 'from'");
        return sessions.findByTenantIdAndStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAtDesc(
                tenantId, to, from, PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public List<ChargingSession> forCharger(UUID tenantId, UUID chargerId) {
        return sessions.findByTenantIdAndChargerId(tenantId, chargerId);
    }

    @Transactional
    public ChargingSession cancel(UUID tenantId, String actor, UUID sessionId) {
        return transition(tenantId, actor, sessionId, "CANCEL_SESSION", "SESSION_CANCELLED", ChargingSession::cancel);
    }

    @Transactional
    public ChargingSession start(UUID tenantId, String actor, UUID sessionId) {
        return transition(tenantId, actor, sessionId, "START_SESSION", "SESSION_STARTED", ChargingSession::start);
    }

    @Transactional
    public ChargingSession complete(UUID tenantId, String actor, UUID sessionId) {
        return transition(tenantId, actor, sessionId, "COMPLETE_SESSION", "SESSION_COMPLETED", ChargingSession::complete);
    }

    @Transactional
    public ChargingSession interrupt(UUID tenantId, String actor, UUID sessionId, String reason) {
        return transition(tenantId, actor, sessionId, "INTERRUPT_SESSION", "SESSION_INTERRUPTED", s -> s.interrupt(reason));
    }

    private ChargingSession transition(UUID tenantId, String actor, UUID sessionId, String action, String eventType,
                                       Consumer<ChargingSession> change) {
        ChargingSession s = sessions.findByIdAndTenantId(sessionId, tenantId)
                .orElseThrow(() -> new NotFoundException("session", sessionId));
        change.accept(s);
        audit.record(tenantId, actor, action, "charging_session", sessionId.toString(), Map.of());
        publish(s, eventType);
        return s;
    }

    /** Integration event for other services (e.g. notifications), written in this same transaction. */
    private void publish(ChargingSession s, String eventType) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", s.getId().toString());
        data.put("vehicleId", s.getVehicleId().toString());
        data.put("chargerId", s.getChargerId().toString());
        data.put("startsAt", s.getWindow().start().toString());
        data.put("endsAt", s.getWindow().end().toString());
        data.put("powerKw", s.getPower().value());
        data.put("status", s.getStatus().name());
        if (s.getInterruptionReason() != null) data.put("reason", s.getInterruptionReason());
        outbox.record(s.getTenantId(), "charging_session", s.getId(), eventType, data);
    }
}
