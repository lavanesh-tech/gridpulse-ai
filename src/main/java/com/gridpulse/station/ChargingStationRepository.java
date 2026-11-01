package com.gridpulse.station;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChargingStationRepository extends JpaRepository<ChargingStation, UUID> {
    List<ChargingStation> findByTenantId(UUID tenantId);

    Optional<ChargingStation> findByIdAndTenantId(UUID id, UUID tenantId);
}
