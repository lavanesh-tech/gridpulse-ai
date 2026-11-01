package com.gridpulse.vehicle;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {
    List<Vehicle> findByTenantIdAndFleetId(UUID tenantId, UUID fleetId);

    Optional<Vehicle> findByIdAndTenantId(UUID id, UUID tenantId);
}
