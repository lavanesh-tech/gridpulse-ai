package com.gridpulse.session;

import com.gridpulse.shared.domain.PowerKw;
import com.gridpulse.shared.domain.TimeWindow;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChargingSessionTest {
    private ChargingSession newSession() {
        Instant t = Instant.parse("2026-01-05T00:00:00Z");
        return new ChargingSession(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new TimeWindow(t, t.plusSeconds(3600)), PowerKw.of(50));
    }

    @Test
    void happyPathLifecycle() {
        ChargingSession s = newSession();
        s.start();
        s.complete();
        assertThat(s.getStatus()).isEqualTo(SessionStatus.COMPLETED);
    }

    @Test
    void interruptionRecordsReason() {
        ChargingSession s = newSession();
        s.start();
        s.interrupt("station outage");
        assertThat(s.getStatus()).isEqualTo(SessionStatus.INTERRUPTED);
        assertThat(s.getInterruptionReason()).isEqualTo("station outage");
    }

    @Test
    void illegalTransitionsAreRejected() {
        ChargingSession s = newSession();
        assertThatThrownBy(s::complete).isInstanceOf(IllegalStateException.class);
        s.cancel();
        assertThatThrownBy(s::start).isInstanceOf(IllegalStateException.class);
    }
}
