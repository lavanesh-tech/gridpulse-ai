package com.gridpulse.shared.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValueObjectTest {

    @Test
    void powerRejectsNegative() {
        assertThatThrownBy(() -> PowerKw.of(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void powerOverTimeGivesEnergy() {
        assertThat(PowerKw.of(50).over(30)).isEqualTo(EnergyKwh.of(25));
    }

    @Test
    void powerExceedsLimit() {
        assertThat(PowerKw.of(150.001).exceeds(PowerKw.of(150))).isTrue();
        assertThat(PowerKw.of(150).exceeds(PowerKw.of(150))).isFalse();
    }

    @Test
    void stateOfChargeBounds() {
        assertThatThrownBy(() -> new StateOfCharge(101)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StateOfCharge(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void energyNeededFromSocToTarget() {
        var needed = new StateOfCharge(20).energyNeededTo(new StateOfCharge(80), EnergyKwh.of(100));
        assertThat(needed).isEqualTo(EnergyKwh.of(60));
        assertThat(new StateOfCharge(90).energyNeededTo(new StateOfCharge(80), EnergyKwh.of(100)))
                .isEqualTo(EnergyKwh.ZERO);
    }

    @Test
    void timeWindowsTouchingDoNotOverlap() {
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        var a = new TimeWindow(t0, t0.plusSeconds(3600));
        var b = new TimeWindow(t0.plusSeconds(3600), t0.plusSeconds(7200));
        var c = new TimeWindow(t0.plusSeconds(1800), t0.plusSeconds(5400));
        assertThat(a.overlaps(b)).isFalse();
        assertThat(a.overlaps(c)).isTrue();
        assertThat(a.minutes()).isEqualTo(60);
    }

    @Test
    void timeWindowRejectsEmpty() {
        Instant t0 = Instant.now();
        assertThatThrownBy(() -> new TimeWindow(t0, t0)).isInstanceOf(IllegalArgumentException.class);
    }
}
