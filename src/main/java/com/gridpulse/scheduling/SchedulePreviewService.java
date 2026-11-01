package com.gridpulse.scheduling;

import com.gridpulse.shared.domain.ConnectorType;
import com.gridpulse.shared.domain.EnergyKwh;
import com.gridpulse.shared.domain.PowerKw;
import com.gridpulse.shared.domain.StateOfCharge;
import com.gridpulse.shared.error.NotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds a {@link SchedulingProblem} from the tenant's live data and runs a scheduler. Read-only: nothing is booked.
 * Uses Spring JDBC read queries (one join for chargers + station status + grid) instead of loading JPA entity graphs.
 */
@Service
public class SchedulePreviewService {
    private final JdbcTemplate jdbc;

    public SchedulePreviewService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public enum Algorithm { BASELINE, PRIORITY_SLACK }

    public record VehicleNeed(UUID vehicleId, int targetSocPct, Instant arrival, Instant departure, int priority) {
    }

    public record Preview(ChargingSchedule schedule, List<Violation> violations, ScheduleMetrics metrics) {
    }

    private record VehicleRow(ConnectorType connector, EnergyKwh battery, PowerKw rate, int soc) {
    }

    @Transactional(readOnly = true)
    public Preview preview(UUID tenantId, Instant horizonStart, int slotMinutes, int slotCount,
                           Algorithm algorithm, List<VehicleNeed> needs) {
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
        for (VehicleNeed n : needs) {
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

        List<GridLimit> grids = jdbc.query("SELECT id, capacity_limit_kw FROM grid_connection WHERE tenant_id = ?",
                (rs, i) -> new GridLimit(rs.getString("id"), new PowerKw(rs.getBigDecimal("capacity_limit_kw"))), tenantId);

        SchedulingProblem problem = new SchedulingProblem(requests, chargers, grids, horizonStart, slotMinutes, slotCount);
        ChargingScheduler scheduler = algorithm == Algorithm.BASELINE ? new BaselineScheduler() : new PrioritySlackScheduler();
        ChargingSchedule schedule = scheduler.schedule(problem);
        List<Violation> violations = new ScheduleValidator().validate(problem, schedule);
        return new Preview(schedule, violations, ScheduleMetrics.of(problem, schedule, violations));
    }
}
