package com.gridpulse.grid;

import com.gridpulse.audit.AuditLog;
import com.gridpulse.identity.CurrentUser;
import com.gridpulse.shared.domain.PowerKw;
import com.gridpulse.shared.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class GridService {
    private final GridConnectionRepository grids;
    private final AuditLog audit;

    public GridService(GridConnectionRepository grids, AuditLog audit) {
        this.grids = grids;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<GridConnection> list() {
        return grids.findByTenantId(CurrentUser.get().tenantId());
    }

    @Transactional(readOnly = true)
    public GridConnection get(UUID id) {
        return grids.findByIdAndTenantId(id, CurrentUser.get().tenantId())
                .orElseThrow(() -> new NotFoundException("grid connection", id));
    }

    @Transactional
    public GridConnection create(String name, PowerKw capacity, ZoneId timeZone) {
        CurrentUser user = CurrentUser.get();
        GridConnection g = grids.saveAndFlush(new GridConnection(user.tenantId(), name, capacity, timeZone));
        audit.record(user.tenantId(), user.username(), "CREATE_GRID_CONNECTION", "grid_connection",
                g.getId().toString(), Map.of("capacityKw", capacity.value()));
        return g;
    }
}
