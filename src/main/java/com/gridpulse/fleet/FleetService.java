package com.gridpulse.fleet;

import com.gridpulse.audit.AuditLog;
import com.gridpulse.identity.CurrentUser;
import com.gridpulse.shared.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FleetService {
    private final FleetRepository fleets;
    private final AuditLog audit;

    public FleetService(FleetRepository fleets, AuditLog audit) {
        this.fleets = fleets;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Fleet> list() {
        return fleets.findByTenantId(CurrentUser.get().tenantId());
    }

    /** Returns the fleet only if it belongs to the caller's tenant; otherwise 404 (never reveal it exists). */
    @Transactional(readOnly = true)
    public Fleet get(UUID id) {
        return fleets.findByIdAndTenantId(id, CurrentUser.get().tenantId())
                .orElseThrow(() -> new NotFoundException("fleet", id));
    }

    @Transactional
    public Fleet create(String name) {
        CurrentUser user = CurrentUser.get();
        Fleet fleet = fleets.saveAndFlush(new Fleet(user.tenantId(), name));
        audit.record(user.tenantId(), user.username(), "CREATE_FLEET", "fleet", fleet.getId().toString(),
                Map.of("name", fleet.getName()));
        return fleet;
    }
}
