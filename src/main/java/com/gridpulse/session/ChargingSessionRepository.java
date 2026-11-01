package com.gridpulse.session;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChargingSessionRepository extends JpaRepository<ChargingSession, UUID> {
    List<ChargingSession> findByTenantIdAndChargerId(UUID tenantId, UUID chargerId);

    Optional<ChargingSession> findByIdAndTenantId(UUID id, UUID tenantId);

    /** Historical records: sessions overlapping [from, to), newest first. */
    Page<ChargingSession> findByTenantIdAndStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAtDesc(
            UUID tenantId, Instant to, Instant from, Pageable pageable);
}
