package com.gridpulse.station;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChargingStationRepository extends JpaRepository<ChargingStation, UUID> {
    List<ChargingStation> findByTenantId(UUID tenantId);
}
