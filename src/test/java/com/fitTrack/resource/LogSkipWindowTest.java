package com.fitTrack.resource;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogSkipWindowTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 16);

    @Test
    void acceptsTodayAndTheRecentPast() {
        assertTrue(LogResource.isSkippable("2026-11-16", TODAY));
        assertTrue(LogResource.isSkippable("2026-11-15", TODAY));
        assertTrue(LogResource.isSkippable("2026-11-02", TODAY));
    }

    @Test
    void rejectsFutureOldAndMalformedDates() {
        assertFalse(LogResource.isSkippable("2026-11-17", TODAY));
        assertFalse(LogResource.isSkippable("2026-11-01", TODAY));
        assertFalse(LogResource.isSkippable("2026-02-30", TODAY));
        assertFalse(LogResource.isSkippable("yesterday", TODAY));
        assertFalse(LogResource.isSkippable("", TODAY));
        assertFalse(LogResource.isSkippable(null, TODAY));
    }
}
