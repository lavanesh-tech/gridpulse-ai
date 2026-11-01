package com.gridpulse.shared.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Half-open time interval [start, end). Two windows that merely touch do not overlap. */
public record TimeWindow(Instant start, Instant end) {
    public TimeWindow {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("end must be after start");
        }
    }

    public boolean overlaps(TimeWindow other) {
        return start.isBefore(other.end) && other.start.isBefore(end);
    }

    public long minutes() { return Duration.between(start, end).toMinutes(); }
}
