package com.gridpulse.session;

import com.gridpulse.shared.domain.TimeWindow;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Peak combined power of existing sessions inside a window (sweep line over start/end events). */
final class GridLoad {
    record Booking(Instant start, Instant end, BigDecimal powerKw) {
    }

    private GridLoad() {
    }

    static BigDecimal peakWithin(List<Booking> bookings, TimeWindow window) {
        TreeMap<Instant, BigDecimal> deltas = new TreeMap<>();
        for (Booking b : bookings) {
            Instant s = b.start().isBefore(window.start()) ? window.start() : b.start();
            Instant e = b.end().isAfter(window.end()) ? window.end() : b.end();
            if (!e.isAfter(s)) continue;
            deltas.merge(s, b.powerKw(), BigDecimal::add);
            deltas.merge(e, b.powerKw().negate(), BigDecimal::add);
        }
        BigDecimal load = BigDecimal.ZERO;
        BigDecimal peak = BigDecimal.ZERO;
        for (Map.Entry<Instant, BigDecimal> d : new ArrayList<>(deltas.entrySet())) {
            load = load.add(d.getValue());
            if (load.compareTo(peak) > 0) peak = load;
        }
        return peak;
    }
}
