package com.gridpulse.station;

import com.gridpulse.audit.AuditLog;
import com.gridpulse.grid.GridService;
import com.gridpulse.identity.CurrentUser;
import com.gridpulse.shared.domain.ConnectorType;
import com.gridpulse.shared.domain.PowerKw;
import com.gridpulse.shared.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class StationService {
    private final ChargingStationRepository stations;
    private final ChargerRepository chargers;
    private final GridService grids;
    private final AuditLog audit;

    public StationService(ChargingStationRepository stations, ChargerRepository chargers, GridService grids,
                          AuditLog audit) {
        this.stations = stations;
        this.chargers = chargers;
        this.grids = grids;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<ChargingStation> list() {
        return stations.findByTenantId(CurrentUser.get().tenantId());
    }

    @Transactional(readOnly = true)
    public ChargingStation get(UUID id) {
        return stations.findByIdAndTenantId(id, CurrentUser.get().tenantId())
                .orElseThrow(() -> new NotFoundException("station", id));
    }

    @Transactional
    public ChargingStation create(UUID gridConnectionId, String name) {
        grids.get(gridConnectionId);
        CurrentUser user = CurrentUser.get();
        ChargingStation s = stations.saveAndFlush(new ChargingStation(user.tenantId(), gridConnectionId, name));
        audit.record(user.tenantId(), user.username(), "CREATE_STATION", "charging_station", s.getId().toString(),
                Map.of("gridConnectionId", gridConnectionId.toString()));
        return s;
    }

    @Transactional(readOnly = true)
    public List<Charger> chargers(UUID stationId) {
        get(stationId);
        return chargers.findByTenantIdAndStationId(CurrentUser.get().tenantId(), stationId);
    }

    @Transactional
    public Charger addCharger(UUID stationId, String label, ConnectorType connector, PowerKw maxPower) {
        get(stationId);
        CurrentUser user = CurrentUser.get();
        Charger c = chargers.saveAndFlush(new Charger(user.tenantId(), stationId, label, connector, maxPower));
        audit.record(user.tenantId(), user.username(), "ADD_CHARGER", "charger", c.getId().toString(),
                Map.of("stationId", stationId.toString(), "connector", connector.name()));
        return c;
    }

    /** Outage simulation: an offline station's chargers are excluded from reservations and schedules. */
    @Transactional
    public ChargingStation reportOutage(UUID stationId) {
        ChargingStation s = get(stationId);
        s.reportOutage();
        CurrentUser user = CurrentUser.get();
        audit.record(user.tenantId(), user.username(), "STATION_OUTAGE", "charging_station", stationId.toString(), Map.of());
        return s;
    }

    @Transactional
    public ChargingStation restore(UUID stationId) {
        ChargingStation s = get(stationId);
        s.restore();
        CurrentUser user = CurrentUser.get();
        audit.record(user.tenantId(), user.username(), "STATION_RESTORED", "charging_station", stationId.toString(), Map.of());
        return s;
    }
}
