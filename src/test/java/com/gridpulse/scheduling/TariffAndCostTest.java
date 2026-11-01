package com.gridpulse.scheduling;

import com.gridpulse.shared.domain.PowerKw;
import com.gridpulse.shared.domain.TimeWindow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;

import static com.gridpulse.scheduling.Fixtures.T0;
import static com.gridpulse.scheduling.Fixtures.at;
import static com.gridpulse.scheduling.Fixtures.charger;
import static com.gridpulse.scheduling.Fixtures.request;
import static com.gridpulse.shared.domain.ConnectorType.CCS1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TariffAndCostTest {
    /** 00:00–01:00 UTC cheap (0.10), the rest of the day expensive (0.50; band wraps midnight). */
    private static final GridTariff CHEAP_FIRST_HOUR = new GridTariff("g", ZoneOffset.UTC, List.of(
            new TariffRate(LocalTime.of(0, 0), LocalTime.of(1, 0), new BigDecimal("0.10")),
            new TariffRate(LocalTime.of(1, 0), LocalTime.of(0, 0), new BigDecimal("0.50"))));

    private SchedulingProblem problem() {
        return new SchedulingProblem(List.of(request("v", CCS1, 100, 50, 75, 100, 0, 120, 0)),
                List.of(charger("A", CCS1, 100)), List.of(new GridLimit("g", PowerKw.of(200))), T0, 15, 8,
                List.of(CHEAP_FIRST_HOUR), List.of());
    }

    @Test
    void wrappingBandCoversAcrossMidnight() {
        TariffRate night = new TariffRate(LocalTime.of(21, 0), LocalTime.of(6, 0), BigDecimal.ONE);
        assertThat(night.covers(LocalTime.of(23, 0))).isTrue();
        assertThat(night.covers(LocalTime.of(5, 59))).isTrue();
        assertThat(night.covers(LocalTime.of(6, 0))).isFalse();
        assertThat(night.covers(LocalTime.of(12, 0))).isFalse();
    }

    @Test
    void overlappingBandsAreRejected() {
        assertThatThrownBy(() -> new GridTariff("g", ZoneOffset.UTC, List.of(
                new TariffRate(LocalTime.of(0, 0), LocalTime.of(2, 0), BigDecimal.ONE),
                new TariffRate(LocalTime.of(1, 0), LocalTime.of(3, 0), BigDecimal.ONE))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void costSplitsASessionAtTariffBoundaries() {
        // 100 kW from 00:30 to 01:30: 50 kWh at 0.10 + 50 kWh at 0.50 = 30.00
        ChargingSchedule s = new ChargingSchedule("t", List.of(
                new ScheduledSession("v", "A", new TimeWindow(at(30), at(90)), PowerKw.of(100))), List.of());
        ScheduleCost cost = ScheduleCost.of(problem(), s);
        assertThat(cost.totalCost()).isEqualByComparingTo("30.00");
        assertThat(cost.energy().value()).isEqualByComparingTo("100");
        assertThat(cost.averagePricePerKwh()).isEqualByComparingTo("0.30");
    }

    @Test
    void costFirstPicksCheapSlotsWhileDeadlinesFirstPicksLatest() {
        SchedulingProblem p = problem();
        ChargingSchedule cost = new PrioritySlackScheduler(PrioritySlackScheduler.Objective.COST_FIRST).schedule(p);
        ChargingSchedule deadlines = new PrioritySlackScheduler().schedule(p);

        assertThat(cost.sessions()).singleElement().satisfies(x -> assertThat(x.window().end()).isEqualTo(at(60)));
        assertThat(deadlines.sessions()).singleElement().satisfies(x -> assertThat(x.window().end()).isEqualTo(at(120)));
        assertThat(ScheduleCost.of(p, cost).totalCost()).isEqualByComparingTo("2.50");
        assertThat(ScheduleCost.of(p, deadlines).totalCost()).isEqualByComparingTo("12.50");
        assertThat(cost.unmet()).isEmpty();
        assertThat(deadlines.unmet()).isEmpty();
    }

    @Test
    void untariffedProblemCostsZero() {
        SchedulingProblem p = Fixtures.problem(List.of(request("v", CCS1, 100, 0, 50, 100, 0, 120, 0)),
                List.of(charger("A", CCS1, 100)), 100);
        assertThat(ScheduleMetrics.of(p, new PrioritySlackScheduler().schedule(p), List.of()).cost().totalCost())
                .isEqualByComparingTo("0");
    }
}
