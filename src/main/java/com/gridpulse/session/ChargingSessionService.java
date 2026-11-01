package com.gridpulse.session;

import com.gridpulse.shared.domain.PowerKw;
import com.gridpulse.shared.domain.TimeWindow;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ChargingSessionService {
    private final ChargingSessionRepository sessions;

    public ChargingSessionService(ChargingSessionRepository sessions) {
        this.sessions = sessions;
    }

    /**
     * Reserves a charger. Deliberately not wrapped in an outer transaction: saveAndFlush runs in its own
     * transaction, so a constraint violation surfaces here as an exception we can translate, instead of
     * marking an outer transaction rollback-only.
     * Tenant ownership of vehicle and charger is enforced in Milestone 3 (security layer).
     */
    public ChargingSession reserve(UUID tenantId, UUID vehicleId, UUID chargerId, TimeWindow window, PowerKw power) {
        try {
            return sessions.saveAndFlush(new ChargingSession(tenantId, vehicleId, chargerId, window, power));
        } catch (DataIntegrityViolationException e) {
            throw new ReservationConflictException(
                    "charger " + chargerId + " or vehicle " + vehicleId + " already booked during " + window, e);
        }
    }

    @Transactional
    public ChargingSession cancel(UUID sessionId) {
        ChargingSession s = sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("session not found: " + sessionId));
        s.cancel();
        return s;
    }
}
