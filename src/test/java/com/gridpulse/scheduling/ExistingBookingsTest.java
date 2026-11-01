package com.gridpulse.scheduling;

import com.gridpulse.shared.domain.PowerKw;
import com.gridpulse.shared.domain.TimeWindow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.gridpulse.scheduling.Fixtures.T0;
import static com.gridpulse.scheduling.Fixtures.at;
import static com.gridpulse.scheduling.Fixtures.charger;
import static com.gridpulse.scheduling.Fixtures.request;
import static com.gridpulse.shared.domain.ConnectorType.CCS1;
import static org.assertj.core.api.Assertions.assertThat;

/** Schedules must respect capacity that existing reservations already hold. */
class ExistingBookingsTest {
    private final ScheduleValidator validator = new ScheduleValidator();

    private SchedulingProblem withBooking(double gridKw, FixedBooking booking) {
        return new SchedulingProblem(List.of(request("v", CCS1, 100, 0, 50, 100, 0, 120, 0)),
                List.of(charger("A", CCS1, 100), charger("B", CCS1, 100)),
                List.of(new GridLimit("g", PowerKw.of(gridKw))), T0, 15, 8, List.of(), List.of(booking));
    }

    @Test
    void existingBookingThatFillsTheGridIsPlannedAround() {
        SchedulingProblem p = withBooking(100, new FixedBooking("other", "A", new TimeWindow(at(0), at(60)), PowerKw.of(100)));
        for (ChargingScheduler s : List.of(new BaselineScheduler(), new PrioritySlackScheduler())) {
            ChargingSchedule result = s.schedule(p);
            assertThat(validator.validate(p, result)).as(s.name()).isEmpty();
            assertThat(result.unmet()).as(s.name()).isEmpty();
            assertThat(result.sessions()).allSatisfy(x -> assertThat(x.window().start()).isAfterOrEqualTo(at(60)));
        }
    }

    @Test
    void vehicleWithAnExistingBookingIsNotPlacedOnASecondChargerAtTheSameTime() {
        SchedulingProblem p = withBooking(300, new FixedBooking("v", "A", new TimeWindow(at(60), at(120)), PowerKw.of(50)));
        ChargingSchedule result = new PrioritySlackScheduler().schedule(p);
        assertThat(validator.validate(p, result)).isEmpty();
        assertThat(result.sessions()).allSatisfy(x -> assertThat(x.window().end()).isBeforeOrEqualTo(at(60)));
    }

    @Test
    void validatorCountsExistingBookingsAsOccupied() {
        SchedulingProblem p = withBooking(300, new FixedBooking(null, "A", new TimeWindow(at(0), at(60)), PowerKw.of(50)));
        ChargingSchedule overlapping = new ChargingSchedule("t", List.of(
                new ScheduledSession("v", "A", new TimeWindow(at(30), at(60)), PowerKw.of(10))), List.of());
        assertThat(validator.validate(p, overlapping)).extracting(Violation::type)
                .containsExactly(ViolationType.CHARGER_DOUBLE_BOOKED);
    }

    @Test
    void gridProfileIncludesExistingBookingsAndFlagsBottlenecks() {
        SchedulingProblem p = withBooking(100, new FixedBooking("other", "A", new TimeWindow(at(0), at(60)), PowerKw.of(100)));
        GridLoadProfile profile = GridLoadProfile.of(p, new ChargingSchedule("t", List.of(), List.of())).get(0);
        assertThat(profile.peak().value()).isEqualByComparingTo("100");
        assertThat(profile.peakUtilization()).isEqualTo(1.0);
        assertThat(profile.bottleneckSlots()).containsExactly(0, 1, 2, 3);
    }
}
