package com.gridpulse.grid;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GridConnectionRepository extends JpaRepository<GridConnection, UUID> {
    List<GridConnection> findByTenantId(UUID tenantId);
}
