package com.gridpulse.fleet;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FleetRepository extends JpaRepository<Fleet, UUID> {
    List<Fleet> findByTenantId(UUID tenantId);

    Optional<Fleet> findByIdAndTenantId(UUID id, UUID tenantId);
}
