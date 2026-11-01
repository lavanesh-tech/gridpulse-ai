package com.gridpulse.analytics;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dashboard aggregates over recorded charging sessions, computed in SQL (one round trip per figure set).
 * Energy is the scheduled energy (power × duration), not metered energy, since no meter data exists yet.
 */
@Service
public class AnalyticsService {
    private final JdbcTemplate jdbc;

    public AnalyticsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record ChargerUsage(UUID chargerId, String label, String station, long sessions,
                               BigDecimal occupiedHours, double utilization) {
    }

    public record Summary(Instant from, Instant to, Map<String, Long> sessionsByStatus, BigDecimal scheduledEnergyKwh,
                          long vehiclesServed, List<ChargerUsage> chargers) {
    }

    @Transactional(readOnly = true)
    public Summary summary(UUID tenantId, Instant from, Instant to) {
        if (!to.isAfter(from)) throw new IllegalArgumentException("'to' must be after 'from'");
        if (Duration.between(from, to).toDays() > 366) throw new IllegalArgumentException("range is limited to 366 days");
        Timestamp f = Timestamp.from(from);
        Timestamp t = Timestamp.from(to);

        Map<String, Long> byStatus = new LinkedHashMap<>();
        jdbc.query("""
                SELECT status, count(*) AS n FROM charging_session
                WHERE tenant_id = ? AND starts_at < ? AND ends_at > ? GROUP BY status ORDER BY status
                """, rs -> {
            byStatus.put(rs.getString("status"), rs.getLong("n"));
        }, tenantId, t, f);

        // Energy and occupancy clipped to [from, to); cancelled sessions never delivered anything.
        Map<String, Object> totals = jdbc.queryForMap("""
                SELECT COALESCE(SUM(power_kw * EXTRACT(EPOCH FROM (LEAST(ends_at, ?) - GREATEST(starts_at, ?))) / 3600), 0) AS kwh,
                       COUNT(DISTINCT vehicle_id) AS vehicles
                FROM charging_session
                WHERE tenant_id = ? AND status <> 'CANCELLED' AND starts_at < ? AND ends_at > ?
                """, t, f, tenantId, t, f);

        double rangeHours = Duration.between(from, to).toSeconds() / 3600.0;
        List<ChargerUsage> chargers = jdbc.query("""
                SELECT c.id, c.label, s.name AS station, COUNT(cs.id) AS sessions,
                       COALESCE(SUM(EXTRACT(EPOCH FROM (LEAST(cs.ends_at, ?) - GREATEST(cs.starts_at, ?))) / 3600), 0) AS hours
                FROM charger c
                JOIN charging_station s ON s.id = c.station_id
                LEFT JOIN charging_session cs ON cs.charger_id = c.id AND cs.status <> 'CANCELLED'
                     AND cs.starts_at < ? AND cs.ends_at > ?
                WHERE c.tenant_id = ?
                GROUP BY c.id, c.label, s.name
                ORDER BY hours DESC, c.label
                """, (rs, i) -> {
            BigDecimal hours = rs.getBigDecimal("hours").setScale(2, RoundingMode.HALF_UP);
            return new ChargerUsage(UUID.fromString(rs.getString("id")), rs.getString("label"), rs.getString("station"),
                    rs.getLong("sessions"), hours, Math.round(10_000 * hours.doubleValue() / rangeHours) / 10_000.0);
        }, t, f, t, f, tenantId);

        return new Summary(from, to, byStatus, ((BigDecimal) totals.get("kwh")).setScale(3, RoundingMode.HALF_UP),
                ((Number) totals.get("vehicles")).longValue(), chargers);
    }
}
