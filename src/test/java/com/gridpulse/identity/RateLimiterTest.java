package com.gridpulse.identity;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {
    @Test
    void allowsBurstUpToCapacityThenRefillsOverTime() {
        AtomicLong now = new AtomicLong(0);
        RateLimiter limiter = new RateLimiter(60, now::get); // 1 token per second

        for (int i = 0; i < 60; i++) {
            assertThat(limiter.tryAcquire("u")).isTrue();
        }
        assertThat(limiter.tryAcquire("u")).isFalse();

        now.addAndGet(1_000_000_000L); // +1 s
        assertThat(limiter.tryAcquire("u")).isTrue();
        assertThat(limiter.tryAcquire("u")).isFalse();
    }

    @Test
    void keysAreIndependent() {
        RateLimiter limiter = new RateLimiter(1, () -> 0L);
        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isFalse();
        assertThat(limiter.tryAcquire("b")).isTrue();
    }
}
