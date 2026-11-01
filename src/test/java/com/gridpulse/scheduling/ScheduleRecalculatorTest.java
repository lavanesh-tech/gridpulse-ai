package com.gridpulse.scheduling;

import com.gridpulse.shared.domain.PowerKw;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.gridpulse.scheduling.Fixtures.at;
import static com.gridpulse.scheduling.Fixtures.charger;
import static com.gridpulse.scheduling.Fixtures.problem;
import static com.gridpulse.scheduling.Fixtures.request;
import static com.gridpulse.shared.domain.ConnectorType.CCS1;
import static org.assertj.core.api.Assertions.assertThat;

class ScheduleRecalculatorTest {
    private final PrioritySlackScheduler scheduler = new PrioritySlackScheduler();
    private final ScheduleRecalculator recalculator = new ScheduleRecalculator(scheduler);

    /** One vehicle needing 50 kWh, two 100 kW chargers on a 100 kW grid, 2-hour window. Planned on A at 1:30–2:00. */
    private SchedulingProblem problem1() {
        return problem(List.of(request("v1", CCS1, 100, 0, 50, 100, 0, 120, 0)),
                List.of(charger("A", CCS1, 100), charger("B", CCS1, 100)), 100);
    }

    @Test
    void earlyOutageMovesTheVehicleToAnotherCharger() {
        SchedulingProblem p = problem1();
        ChargingSchedule plan = scheduler.schedule(p);
        RecalculationResult r = recalculator.recalculate(p, plan, new Disruption(at(10), Set.of("A"), Map.of()));

        assertThat(r.violations()).isEmpty();
        assertThat(r.schedule().unmet()).isEmpty();
        assertThat(r.schedule().sessions()).allSatisfy(s -> assertThat(s.chargerId()).isEqualTo("B"));
        assertThat(r.replannedFrom()).isEqualTo(at(15));
    }

    @Test
    void lateOutageInterruptsTheSessionAndReportsTheMiss() {
        SchedulingProblem p = problem1();
        ChargingSchedule plan = scheduler.schedule(p);
        RecalculationResult r = recalculator.recalculate(p, plan, new Disruption(at(95), Set.of("A"), Map.of()));

        assertThat(r.violations()).isEmpty();
        assertThat(r.sessionsInterrupted()).isEqualTo(1);
        // 5 min on A before the fault, then B from the next slot boundary: 8.3 + 25 kWh of 50 needed.
        assertThat(r.schedule().unmet()).singleElement().satisfies(u -> {
            assertThat(u.reason()).isEqualTo(UnmetReason.DEADLINE_MISSED);
            assertThat(u.delivered().value()).isEqualByComparingTo("33.333");
        });
        assertThat(r.newlyUnmetVehicles()).containsExactly("v1");
        assertThat(r.schedule().sessions())
                .filteredOn(s -> s.chargerId().equals("A"))
                .allSatisfy(s -> assertThat(s.window().end()).isBeforeOrEqualTo(at(95)));
    }

    @Test
    void gridCurtailmentLowersPowerButStillMeetsTheTarget() {
        SchedulingProblem p = problem1();
        RecalculationResult r = recalculator.recalculate(p, scheduler.schedule(p),
                new Disruption(at(0), Set.of(), Map.of("g", PowerKw.of(40))));
        assertThat(r.violations()).isEmpty();
        assertThat(r.schedule().unmet()).isEmpty();
        assertThat(r.schedule().sessions()).allSatisfy(s -> assertThat(s.power().value()).isLessThanOrEqualTo(new BigDecimal("40")));
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2, 3, 4, 5})
    void generatedDisruptionsNeverViolateConstraintsOrUseFailedChargers(long seed) {
        SchedulingProblem p = SyntheticScenarioGenerator.generate(seed, 300, 4, 15);
        for (ChargingScheduler s : List.of(new BaselineScheduler(), new PrioritySlackScheduler())) {
            ChargingSchedule plan = s.schedule(p);
            Map<String, Integer> use = new HashMap<>();
            plan.sessions().forEach(x -> use.merge(x.chargerId(), 1, Integer::sum));
            String busiest = use.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
            GridLimit g0 = p.grids().get(0);
            Disruption d = new Disruption(p.horizonStart().plus(Duration.ofMinutes(190)), Set.of(busiest),
                    Map.of(g0.gridId(), new PowerKw(g0.capacity().value().multiply(new BigDecimal("0.7")))));

            RecalculationResult r = new ScheduleRecalculator(s).recalculate(p, plan, d);

            assertThat(r.violations()).as("%s seed %d", s.name(), seed).isEmpty();
            assertThat(r.schedule().sessions())
                    .filteredOn(x -> x.chargerId().equals(busiest))
                    .allSatisfy(x -> assertThat(x.window().end()).isBeforeOrEqualTo(d.at()));
        }
    }
}
