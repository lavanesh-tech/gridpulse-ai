package com.gridpulse.scheduling;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static com.gridpulse.scheduling.Fixtures.at;
import static com.gridpulse.scheduling.Fixtures.charger;
import static com.gridpulse.scheduling.Fixtures.problem;
import static com.gridpulse.scheduling.Fixtures.request;
import static com.gridpulse.shared.domain.ConnectorType.CCS1;
import static com.gridpulse.shared.domain.ConnectorType.NACS;
import static org.assertj.core.api.Assertions.assertThat;

class SchedulerTest {
    private final ScheduleValidator validator = new ScheduleValidator();
    private final BaselineScheduler baseline = new BaselineScheduler();
    private final PrioritySlackScheduler optimized = new PrioritySlackScheduler();

    /**
     * Grid 100 kW shared by two 100 kW chargers.
     * v1 arrives first, needs 100 kWh, leaves at 2:00 (lots of slack).
     * v2 arrives at 0:15, needs 25 kWh, leaves at 1:00 (tight).
     */
    private SchedulingProblem sharedGrid() {
        return problem(
                List.of(request("v1", CCS1, 100, 0, 100, 100, 0, 120, 0),
                        request("v2", CCS1, 100, 50, 75, 100, 15, 60, 0)),
                List.of(charger("A", CCS1, 100), charger("B", CCS1, 100)),
                100);
    }

    @Test
    void baselineMissesTightDeadlineWhenFirstArrivalHogsTheGrid() {
        SchedulingProblem p = sharedGrid();
        ChargingSchedule s = baseline.schedule(p);

        assertThat(validator.validate(p, s)).isEmpty();
        assertThat(s.unmet()).singleElement().satisfies(u -> {
            assertThat(u.vehicleId()).isEqualTo("v2");
            assertThat(u.reason()).isEqualTo(UnmetReason.NO_CAPACITY_BEFORE_DEPARTURE);
        });
    }

    @Test
    void optimizedMeetsBothDeadlinesByServingLeastSlackFirst() {
        SchedulingProblem p = sharedGrid();
        ChargingSchedule s = optimized.schedule(p);

        assertThat(validator.validate(p, s)).isEmpty();
        assertThat(s.unmet()).isEmpty();
        assertThat(s.sessions()).filteredOn(x -> x.vehicleId().equals("v2"))
                .allSatisfy(x -> assertThat(x.window().end()).isBeforeOrEqualTo(at(60)));
    }

    @Test
    void incompatibleVehicleIsReportedNotDropped() {
        SchedulingProblem p = problem(List.of(request("v", NACS, 80, 20, 80, 150, 0, 120, 0)),
                List.of(charger("A", CCS1, 150)), 500);
        for (ChargingScheduler scheduler : List.of(baseline, optimized)) {
            ChargingSchedule s = scheduler.schedule(p);
            assertThat(s.sessions()).isEmpty();
            assertThat(s.unmet()).singleElement()
                    .extracting(UnmetRequest::reason).isEqualTo(UnmetReason.NO_COMPATIBLE_CHARGER);
        }
    }

    @Test
    void outOfServiceChargerIsNeverUsed() {
        var down = new ChargerSpec("A", "g", CCS1, com.gridpulse.shared.domain.PowerKw.of(150), false);
        SchedulingProblem p = problem(List.of(request("v", CCS1, 80, 20, 80, 150, 0, 120, 0)), List.of(down), 500);
        assertThat(optimized.schedule(p).sessions()).isEmpty();
        assertThat(baseline.schedule(p).sessions()).isEmpty();
    }

    @Test
    void vehicleAlreadyAtTargetNeedsNoSession() {
        SchedulingProblem p = problem(List.of(request("v", CCS1, 80, 90, 80, 150, 0, 120, 0)),
                List.of(charger("A", CCS1, 150)), 500);
        ChargingSchedule s = optimized.schedule(p);
        assertThat(s.sessions()).isEmpty();
        assertThat(s.unmet()).isEmpty();
    }

    @Test
    void optimizedChargesBelowFullPowerToFitUnderGridLimit() {
        // Two vehicles at the same time, 60 kW grid, 50 kW chargers: both must share.
        SchedulingProblem p = problem(
                List.of(request("v1", CCS1, 100, 50, 60, 50, 0, 120, 0),
                        request("v2", CCS1, 100, 50, 60, 50, 0, 120, 0)),
                List.of(charger("A", CCS1, 50), charger("B", CCS1, 50)), 60);
        ChargingSchedule s = optimized.schedule(p);
        assertThat(validator.validate(p, s)).isEmpty();
        assertThat(s.unmet()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10})
    void generatedScenariosNeverViolateHardConstraints(long seed) {
        SchedulingProblem p = SyntheticScenarioGenerator.generate(seed, 300, 4, 15);
        for (ChargingScheduler scheduler : List.of(baseline, optimized)) {
            assertThat(validator.validate(p, scheduler.schedule(p)))
                    .as("%s seed %d", scheduler.name(), seed).isEmpty();
        }
    }

    @Test
    void metricsBreakDeadlinesDownByPriority() {
        SchedulingProblem p = sharedGrid();
        ScheduleMetrics m = ScheduleMetrics.of(p, baseline.schedule(p), List.of());
        assertThat(m.byPriority()).singleElement().satisfies(o -> {
            assertThat(o.priority()).isZero();
            assertThat(o.needingCharge()).isEqualTo(2);
            assertThat(o.met()).isEqualTo(1);
        });
    }

    @Test
    void costObjectiveIsAlsoValidOnGeneratedScenarios() {
        PrioritySlackScheduler cost = new PrioritySlackScheduler(PrioritySlackScheduler.Objective.COST_FIRST);
        for (long seed = 1; seed <= 5; seed++) {
            SchedulingProblem p = SyntheticScenarioGenerator.generate(seed, 300, 4, 15);
            assertThat(validator.validate(p, cost.schedule(p))).as("seed %d", seed).isEmpty();
        }
    }

    @Test
    void sameSeedProducesSameSchedule() {
        SchedulingProblem a = SyntheticScenarioGenerator.generate(7, 200, 3, 10);
        SchedulingProblem b = SyntheticScenarioGenerator.generate(7, 200, 3, 10);
        assertThat(a).isEqualTo(b);
        assertThat(optimized.schedule(a)).isEqualTo(optimized.schedule(b));
    }
}
