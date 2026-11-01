package com.gridpulse.session;

import com.gridpulse.PostgresIntegrationTest;
import com.gridpulse.fleet.Fleet;
import com.gridpulse.fleet.FleetRepository;
import com.gridpulse.grid.GridConnection;
import com.gridpulse.grid.GridConnectionRepository;
import com.gridpulse.shared.domain.ConnectorType;
import com.gridpulse.shared.domain.EnergyKwh;
import com.gridpulse.shared.domain.PowerKw;
import com.gridpulse.shared.domain.StateOfCharge;
import com.gridpulse.shared.domain.TimeWindow;
import com.gridpulse.station.Charger;
import com.gridpulse.station.ChargerRepository;
import com.gridpulse.station.ChargingStation;
import com.gridpulse.station.ChargingStationRepository;
import com.gridpulse.vehicle.Vehicle;
import com.gridpulse.vehicle.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Proves the database, not just Java code, prevents double-booking — including under concurrency. */
@SpringBootTest
class ChargingReservationIT extends PostgresIntegrationTest {
    private static final Instant T = Instant.parse("2026-01-05T22:00:00Z");
    private static final Random RND = new Random();

    @Autowired ChargingSessionService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired FleetRepository fleets;
    @Autowired VehicleRepository vehicles;
    @Autowired GridConnectionRepository grids;
    @Autowired ChargingStationRepository stations;
    @Autowired ChargerRepository chargers;

    UUID tenant;
    UUID vehicle1;
    UUID vehicle2;
    UUID charger1;
    UUID charger2;

    @BeforeEach
    void seed() {
        tenant = UUID.randomUUID();
        jdbc.update("INSERT INTO tenant (id, name) VALUES (?, ?)", tenant, "tenant-" + tenant);
        Fleet fleet = fleets.save(new Fleet(tenant, "Depot fleet"));
        vehicle1 = vehicles.save(vehicle(fleet)).getId();
        vehicle2 = vehicles.save(vehicle(fleet)).getId();
        GridConnection grid = grids.save(new GridConnection(tenant, "Main feed", PowerKw.of(300)));
        ChargingStation station = stations.save(new ChargingStation(tenant, grid.getId(), "Depot A"));
        charger1 = chargers.save(new Charger(tenant, station.getId(), "A1", ConnectorType.CCS1, PowerKw.of(150))).getId();
        charger2 = chargers.save(new Charger(tenant, station.getId(), "A2", ConnectorType.CCS1, PowerKw.of(150))).getId();
    }

    private Vehicle vehicle(Fleet fleet) {
        return new Vehicle(tenant, fleet.getId(), randomVin(), "Synthetic Van", EnergyKwh.of(100),
                PowerKw.of(150), ConnectorType.CCS1, new StateOfCharge(30));
    }

    private static String randomVin() {
        String alphabet = "ABCDEFGHJKLMNPRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 17; i++) sb.append(alphabet.charAt(RND.nextInt(alphabet.length())));
        return sb.toString();
    }

    private static TimeWindow window(int fromMin, int toMin) {
        return new TimeWindow(T.plusSeconds(fromMin * 60L), T.plusSeconds(toMin * 60L));
    }

    @Test
    void concurrentOverlappingReservationsOnOneChargerOnlyOneSucceeds() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Callable<Boolean>> attempts = List.of(
                () -> attempt(go, vehicle1, charger1, window(0, 60)),
                () -> attempt(go, vehicle2, charger1, window(30, 90)));
        List<Future<Boolean>> futures = new ArrayList<>();
        for (Callable<Boolean> a : attempts) futures.add(pool.submit(a));
        go.countDown();
        int successes = 0;
        for (Future<Boolean> f : futures) if (f.get()) successes++;
        pool.shutdown();

        assertThat(successes).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM charging_session WHERE charger_id = ?",
                Integer.class, charger1)).isEqualTo(1);
    }

    private boolean attempt(CountDownLatch go, UUID vehicle, UUID charger, TimeWindow w) throws InterruptedException {
        go.await();
        try {
            service.reserve(tenant, vehicle, charger, w, PowerKw.of(100));
            return true;
        } catch (ReservationConflictException e) {
            return false;
        }
    }

    @Test
    void backToBackReservationsBothSucceed() {
        service.reserve(tenant, vehicle1, charger1, window(0, 60), PowerKw.of(100));
        service.reserve(tenant, vehicle2, charger1, window(60, 120), PowerKw.of(100));
    }

    @Test
    void vehicleCannotBeOnTwoChargersAtOnce() {
        service.reserve(tenant, vehicle1, charger1, window(0, 60), PowerKw.of(100));
        assertThatThrownBy(() -> service.reserve(tenant, vehicle1, charger2, window(30, 90), PowerKw.of(100)))
                .isInstanceOf(ReservationConflictException.class);
    }

    @Test
    void cancelledReservationFreesTheSlot() {
        ChargingSession s = service.reserve(tenant, vehicle1, charger1, window(0, 60), PowerKw.of(100));
        service.cancel(s.getId());
        service.reserve(tenant, vehicle2, charger1, window(0, 60), PowerKw.of(100));
    }
}
