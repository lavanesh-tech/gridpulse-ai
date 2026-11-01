package com.gridpulse.identity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Token bucket per key: holds up to {@code perMinute} tokens, refills continuously at perMinute/60 per second.
 * In-memory, so limits are per application instance (see ADR-014).
 */
final class RateLimiter {
    private static final int MAX_KEYS = 100_000;

    private final double capacity;
    private final double tokensPerNano;
    private final LongSupplier clock;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    private static final class Bucket {
        double tokens;
        long lastRefill;

        Bucket(double tokens, long now) {
            this.tokens = tokens;
            this.lastRefill = now;
        }
    }

    RateLimiter(int perMinute) {
        this(perMinute, System::nanoTime);
    }

    RateLimiter(int perMinute, LongSupplier clock) {
        if (perMinute <= 0) throw new IllegalArgumentException("perMinute must be > 0");
        this.capacity = perMinute;
        this.tokensPerNano = perMinute / 60_000_000_000.0;
        this.clock = clock;
    }

    boolean tryAcquire(String key) {
        if (buckets.size() > MAX_KEYS) {
            buckets.clear();
        }
        long now = clock.getAsLong();
        Bucket b = buckets.computeIfAbsent(key, k -> new Bucket(capacity, now));
        synchronized (b) {
            b.tokens = Math.min(capacity, b.tokens + (now - b.lastRefill) * tokensPerNano);
            b.lastRefill = now;
            if (b.tokens >= 1.0) {
                b.tokens -= 1.0;
                return true;
            }
            return false;
        }
    }
}
