package com.gridpulse.scheduling;

import com.gridpulse.shared.domain.ConnectorType;
import com.gridpulse.shared.domain.EnergyKwh;
import com.gridpulse.shared.domain.PowerKw;
import com.gridpulse.shared.domain.StateOfCharge;
import com.gridpulse.shared.domain.TimeWindow;
import com.gridpulse.shared.error.NotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Builds a {@link SchedulingProblem} from the tenant's live data (vehicles, chargers + station status, grid limits,
 * tariffs, and capacity already held by live reservations) and runs a scheduler. Read-only: nothing is booked.
 * Uses Spring JDBC read queries that select exactly the columns the engine needs.
 */
@Service
public class SchedulePreviewService {
    private final JdbcTemplate jdbc;

    public SchedulePreviewService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public enum Algorithm { BASELINE, PRIORITY_SLACK, PRIORITY_SLACK_COST }

    public record VehicleNeed(UUID vehicleId, int targetSocPct, Instant arrival, Instant departure, int priority) {
    }

    public record PreviewInput(Instant horizonStart, int slotMinutes, int slotCount, Algorithm algorithm,
                               List<VehicleNeed> needs) {
    }

    public record Preview(ChargingSchedule schedule, List<Violation> violations, ScheduleMetrics metrics,
                          List<GridLoadProfile> gridProfiles, int existingBookingsConsidered) {
    }

    public record Recalculation(Preview before, RecalculationResult result, ScheduleMetrics metricsAfter,
                                List<GridLoadProfile> gridProfilesAfter) {
    }

    private record VehicleRow(ConnectorType connector, EnergyKwh battery, PowerKw rate, int soc) {
    }

    @Transactional(readOnly = true)
    public Preview preview(UUID tenantId, PreviewInput input) {
        SchedulingProblem problem = load(tenantId, input);
        return run(problem, scheduler(input.algorithm()));
    }

    /**
     * What-if: plan, then apply a disruption (outage and/or curtailment) and re-plan the remainder.
     * Charger and grid ids must belong to the tenant.
     */
    @Transactional(readOnly = true)
    public Recalculation recalculate(UUID tenantId, PreviewInput input, Instant at, Set<UUID> chargersOut,
                                     Map<UUID, PowerKw> gridOverrides) {
        SchedulingProblem problem = load(tenantId, input);
        Set<String> chargerIds = new java.util.HashSet<>();
        problem.chargers().forEach(c -> chargerIds.add(c.chargerId()));
        Set<String> gridIds = new java.util.HashSet<>();
        problem.grids().forEach(g -> gridIds.add(g.gridId()));
        Set<String> out = new java.util.HashSet<>();
        for (UUID c : chargersOut) {
            if (!chargerIds.contains(c.toString())) throw new NotFoundException("charger", c);
            out.add(c.toString());
        }
        Map<String, PowerKw> overrides = new HashMap<>();
        gridOverrides.forEach((g, kw) -> {
            if (!gridIds.contains(g.toString())) throw new NotFoundException("grid connection", g);
            overrides.put(g.toString(), kw);
        });

        ChargingScheduler scheduler = scheduler(input.algorithm());
        Preview before = run(problem, scheduler);
        RecalculationResult result = new ScheduleRecalculator(scheduler)
                .recalculate(problem, before.schedule(), new Disruption(at, out, overrides));
        return new Recalculation(before, result, ScheduleMetrics.of(problem, result.schedule(), result.violations()),
                GridLoadProfile.of(problem, result.schedule()));
    }

    private static ChargingScheduler scheduler(Algorithm a) {
        return switch (a) {
            case BASELINE -> new BaselineScheduler();
            case PRIORITY_SLACK -> new PrioritySlackScheduler(PrioritySlackScheduler.Objective.DEADLINES_FIRST);
            case PRIORITY_SLACK_COST -> new PrioritySlackScheduler(PrioritySlackScheduler.Objective.COST_FIRST);
        };
    }

    private static Preview run(SchedulingProblem problem, ChargingScheduler scheduler) {
        ChargingSchedule schedule = scheduler.schedule(problem);
        List<Violation> violations = new ScheduleValidator().validate(problem, schedule);
        return new Preview(schedule, violations, ScheduleMetrics.of(problem, schedule, violations),
                GridLoadProfile.of(problem, schedule), problem.fixedBookings().size());
    }

    private SchedulingProblem load(UUID tenantId, PreviewInput in) {
        Map<String, VehicleRow> vehicleRows = new HashMap<>();
        jdbc.query("""
                SELECT id, connector_type, battery_capacity_kwh, max_charge_rate_kw, state_of_charge_pct
                FROM vehicle WHERE tenant_id = ?
                """, rs -> {
            vehicleRows.put(rs.getString("id"), new VehicleRow(ConnectorType.valueOf(rs.getString("connector_type")),
                    new EnergyKwh(rs.getBigDecimal("battery_capacity_kwh")), new PowerKw(rs.getBigDecimal("max_charge_rate_kw")),
                    rs.getInt("state_of_charge_pct")));
        }, tenantId);

        List<ChargingRequest> requests = new ArrayList<>();
        for (VehicleNeed n : in.needs()) {
            VehicleRow v = vehicleRows.get(n.vehicleId().toString());
            if (v == null) throw new NotFoundException("vehicle", n.vehicleId());
            requests.add(new ChargingRequest(n.vehicleId().toString(), v.connector(), v.battery(),
                    new StateOfCharge(v.soc()), new StateOfCharge(n.targetSocPct()), v.rate(),
                    n.arrival(), n.departure(), n.priority()));
        }

        List<ChargerSpec> chargers = jdbc.query("""
                SELECT c.id, c.connector_type, c.max_power_kw, s.grid_connection_id,
                       (c.status = 'AVAILABLE' AND s.status = 'ONLINE') AS available
                FROM charger c JOIN charging_station s ON s.id = c.station_id
                WHERE c.tenant_id = ?
                """, (rs, i) -> new ChargerSpec(rs.getString("id"), rs.getString("grid_connection_id"),
                ConnectorType.valueOf(rs.getString("connector_type")), new PowerKw(rs.getBigDecimal("max_power_kw")),
                rs.getBoolean("available")), tenantId);

        Map<String, ZoneId> zones = new HashMap<>();
        List<GridLimit> grids = jdbc.query("SELECT id, capacity_limit_kw, time_zone FROM grid_connection WHERE tenant_id = ?",
                (rs, i) -> {
                    zones.put(rs.getString("id"), ZoneId.of(rs.getString("time_zone")));
                    return new GridLimit(rs.getString("id"), new PowerKw(rs.getBigDecimal("capacity_limit_kw")));
                }, tenantId);

        Map<String, List<TariffRate>> rates = new LinkedHashMap<>();
        jdbc.query("""
                SELECT grid_connection_id, starts_at, ends_at, price_per_kwh FROM tariff_period
                WHERE tenant_id = ? ORDER BY grid_connection_id, starts_at
                """, rs -> {
            rates.computeIfAbsent(rs.getString("grid_connection_id"), k -> new ArrayList<>())
                    .add(new TariffRate(rs.getTime("starts_at").toLocalTime(), rs.getTime("ends_at").toLocalTime(),
                            rs.getBigDecimal("price_per_kwh")));
        }, tenantId);
        List<GridTariff> tariffs = new ArrayList<>();
        rates.forEach((grid, list) -> tariffs.add(new GridTariff(grid, zones.getOrDefault(grid, ZoneId.of("UTC")), list)));

        Instant horizonEnd = in.horizonStart().plusSeconds((long) in.slotMinutes() * in.slotCount() * 60);
        List<FixedBooking> bookings = jdbc.query("""
                SELECT vehicle_id, charger_id, starts_at, ends_at, power_kw FROM charging_session
                WHERE tenant_id = ? AND status IN ('RESERVED','ACTIVE') AND starts_at < ? AND ends_at > ?
                """, (rs, i) -> new FixedBooking(rs.getString("vehicle_id"), rs.getString("charger_id"),
                new TimeWindow(rs.getTimestamp("starts_at").toInstant(), rs.getTimestamp("ends_at").toInstant()),
                new PowerKw(rs.getBigDecimal("power_kw"))), tenantId, Timestamp.from(horizonEnd), Timestamp.from(in.horizonStart()));

        return new SchedulingProblem(requests, chargers, grids, in.horizonStart(), in.slotMinutes(), in.slotCount(),
                tariffs, bookings);
    }
}
