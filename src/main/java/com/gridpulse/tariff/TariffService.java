package com.gridpulse.tariff;

import com.gridpulse.audit.AuditLog;
import com.gridpulse.grid.GridService;
import com.gridpulse.identity.CurrentUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Time;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Time-of-use tariff per grid connection. Replace-all semantics: a PUT supplies the full set of bands.
 * Stored with Spring JDBC; the scheduling module reads the same table to price schedules.
 */
@Service
public class TariffService {
    static final BigDecimal MAX_PRICE = new BigDecimal("10");

    private final JdbcTemplate jdbc;
    private final GridService grids;
    private final AuditLog audit;

    public TariffService(JdbcTemplate jdbc, GridService grids, AuditLog audit) {
        this.jdbc = jdbc;
        this.grids = grids;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<TariffBand> get(UUID gridId) {
        grids.get(gridId); // tenant check, 404 otherwise
        return jdbc.query("""
                SELECT starts_at, ends_at, price_per_kwh FROM tariff_period
                WHERE tenant_id = ? AND grid_connection_id = ? ORDER BY starts_at
                """, (rs, i) -> new TariffBand(rs.getTime("starts_at").toLocalTime(), rs.getTime("ends_at").toLocalTime(),
                rs.getBigDecimal("price_per_kwh")), CurrentUser.get().tenantId(), gridId);
    }

    @Transactional
    public List<TariffBand> replace(UUID gridId, List<TariffBand> bands) {
        grids.get(gridId);
        validate(bands);
        CurrentUser user = CurrentUser.get();
        jdbc.update("DELETE FROM tariff_period WHERE tenant_id = ? AND grid_connection_id = ?", user.tenantId(), gridId);
        for (TariffBand b : bands) {
            jdbc.update("""
                    INSERT INTO tariff_period (id, tenant_id, grid_connection_id, starts_at, ends_at, price_per_kwh)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), user.tenantId(), gridId, Time.valueOf(b.start()), Time.valueOf(b.end()),
                    b.pricePerKwh());
        }
        audit.record(user.tenantId(), user.username(), "REPLACE_TARIFF", "grid_connection", gridId.toString(),
                Map.of("bands", bands.size()));
        List<TariffBand> sorted = new ArrayList<>(bands);
        sorted.sort(Comparator.comparing(TariffBand::start));
        return sorted;
    }

    /** Bands must be non-empty, minute-aligned, priced 0..10, at most 5 decimals, and must not overlap. */
    static void validate(List<TariffBand> bands) {
        if (bands.size() > 48) throw new IllegalArgumentException("at most 48 tariff bands");
        boolean[] minute = new boolean[24 * 60];
        for (TariffBand b : bands) {
            if (b.start().getSecond() != 0 || b.end().getSecond() != 0 || b.start().getNano() != 0 || b.end().getNano() != 0) {
                throw new IllegalArgumentException("tariff band times must be whole minutes");
            }
            if (b.start().equals(b.end())) throw new IllegalArgumentException("tariff band must not be empty");
            if (b.pricePerKwh().signum() < 0 || b.pricePerKwh().compareTo(MAX_PRICE) > 0) {
                throw new IllegalArgumentException("price per kWh must be between 0 and " + MAX_PRICE);
            }
            if (b.pricePerKwh().stripTrailingZeros().scale() > 5) {
                throw new IllegalArgumentException("price per kWh supports at most 5 decimals");
            }
            int m = b.startMinute();
            while (m != b.endMinute()) {
                if (minute[m]) throw new IllegalArgumentException("tariff bands overlap at " + String.format("%02d:%02d", m / 60, m % 60));
                minute[m] = true;
                m = (m + 1) % minute.length;
            }
        }
    }
}
