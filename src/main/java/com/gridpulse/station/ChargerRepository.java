package com.gridpulse.station;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChargerRepository extends JpaRepository<Charger, UUID> {
    List<Charger> findByTenantIdAndStationId(UUID tenantId, UUID stationId);

    Optional<Charger> findByIdAndTenantId(UUID id, UUID tenantId);
}
