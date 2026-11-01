package com.gridpulse.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChargingSessionRepository extends JpaRepository<ChargingSession, UUID> {
    List<ChargingSession> findByTenantIdAndChargerId(UUID tenantId, UUID chargerId);

    Optional<ChargingSession> findByIdAndTenantId(UUID id, UUID tenantId);
}
