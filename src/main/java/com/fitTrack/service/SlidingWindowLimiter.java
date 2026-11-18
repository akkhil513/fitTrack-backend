package com.fitTrack.service;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Allows at most {@code max} calls per key within a rolling window.
 *
 * <p>State is per process, so on Lambda it is per warm container: a best-effort brake on runaway
 * clients (and on Gemini spend), not a hard quota.
 */
public final class SlidingWindowLimiter {

    private static final int MAX_KEYS = 10_000;

    private final int max;
    private final long windowMillis;
    private final ConcurrentHashMap<String, ArrayDeque<Long>> calls = new ConcurrentHashMap<>();

    public SlidingWindowLimiter(int max, Duration window) {
        if (max < 1 || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("max must be positive and window must be longer than zero");
        }
        this.max = max;
        this.windowMillis = window.toMillis();
    }

    public boolean tryAcquire(String key, long nowMillis) {
        if (calls.size() > MAX_KEYS) {
            calls.clear();
        }
        ArrayDeque<Long> deque = calls.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && nowMillis - deque.peekFirst() >= windowMillis) {
                deque.pollFirst();
            }
            if (deque.size() >= max) {
                return false;
            }
            deque.addLast(nowMillis);
            return true;
        }
    }
}
