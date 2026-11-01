package com.gridpulse.scheduling;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.gridpulse.scheduling.Fixtures.charger;
import static com.gridpulse.scheduling.Fixtures.problem;
import static com.gridpulse.scheduling.Fixtures.request;
import static com.gridpulse.scheduling.Fixtures.session;
import static com.gridpulse.shared.domain.ConnectorType.CCS1;
import static com.gridpulse.shared.domain.ConnectorType.NACS;
import static org.assertj.core.api.Assertions.assertThat;

class ScheduleValidatorTest {
    private final ScheduleValidator validator = new ScheduleValidator();

    private final SchedulingProblem p = problem(
            List.of(request("v1", CCS1, 100, 0, 100, 100, 0, 120, 0),
                    request("v2", CCS1, 100, 0, 100, 100, 0, 120, 0),
                    request("n1", NACS, 100, 0, 100, 100, 0, 60, 0)),
            List.of(charger("A", CCS1, 100), charger("B", CCS1, 100)),
            150);

    private List<ViolationType> types(ScheduledSession... sessions) {
        return validator.validate(p, new ChargingSchedule("test", List.of(sessions), List.of()))
                .stream().map(Violation::type).toList();
    }

    @Test
    void backToBackSessionsOnOneChargerAreValid() {
        assertThat(types(session("v1", "A", 0, 30, 100), session("v2", "A", 30, 60, 100))).isEmpty();
    }

    @Test
    void detectsGridCapacityExceeded() {
        assertThat(types(session("v1", "A", 0, 30, 100), session("v2", "B", 15, 45, 100)))
                .containsExactly(ViolationType.GRID_CAPACITY_EXCEEDED);
    }

    @Test
    void detectsChargerDoubleBooking() {
        assertThat(types(session("v1", "A", 0, 30, 50), session("v2", "A", 15, 45, 50)))
                .contains(ViolationType.CHARGER_DOUBLE_BOOKED);
    }

    @Test
    void detectsVehicleOnTwoChargersAtOnce() {
        assertThat(types(session("v1", "A", 0, 30, 50), session("v1", "B", 15, 45, 50)))
                .contains(ViolationType.VEHICLE_ON_MULTIPLE_CHARGERS);
    }

    @Test
    void detectsIncompatibleConnector() {
        assertThat(types(session("n1", "A", 0, 30, 50))).contains(ViolationType.INCOMPATIBLE_CONNECTOR);
    }

    @Test
    void detectsPowerAboveChargerAndVehicleLimits() {
        assertThat(types(session("v1", "A", 0, 15, 120)))
                .contains(ViolationType.POWER_EXCEEDS_CHARGER, ViolationType.POWER_EXCEEDS_VEHICLE);
    }

    @Test
    void detectsChargingAfterDeparture() {
        assertThat(types(session("n1", "A", 45, 75, 50))).contains(ViolationType.OUTSIDE_VEHICLE_WINDOW);
    }
}
