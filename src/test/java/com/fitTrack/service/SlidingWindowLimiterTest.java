package com.fitTrack.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlidingWindowLimiterTest {

    @Test
    void allowsUpToTheLimitThenBlocks() {
        var limiter = new SlidingWindowLimiter(3, Duration.ofMinutes(1));
        assertTrue(limiter.tryAcquire("u", 0));
        assertTrue(limiter.tryAcquire("u", 1_000));
        assertTrue(limiter.tryAcquire("u", 2_000));
        assertFalse(limiter.tryAcquire("u", 3_000));
    }

    @Test
    void allowsAgainOnceOldCallsLeaveTheWindow() {
        var limiter = new SlidingWindowLimiter(2, Duration.ofMinutes(1));
        assertTrue(limiter.tryAcquire("u", 0));
        assertTrue(limiter.tryAcquire("u", 10_000));
        assertFalse(limiter.tryAcquire("u", 59_999));
        assertTrue(limiter.tryAcquire("u", 60_000)); // the first call just expired
        assertFalse(limiter.tryAcquire("u", 60_001));
    }

    @Test
    void usersAreCountedSeparately() {
        var limiter = new SlidingWindowLimiter(1, Duration.ofMinutes(1));
        assertTrue(limiter.tryAcquire("a", 0));
        assertTrue(limiter.tryAcquire("b", 0));
        assertFalse(limiter.tryAcquire("a", 1));
    }

    @Test
    void rejectsBadConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new SlidingWindowLimiter(0, Duration.ofMinutes(1)));
        assertThrows(IllegalArgumentException.class, () -> new SlidingWindowLimiter(1, Duration.ZERO));
    }
}
