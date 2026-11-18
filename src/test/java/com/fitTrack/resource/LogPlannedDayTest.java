package com.fitTrack.resource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogPlannedDayTest {

    @Test
    void plannedDayIsNormalisedOrRejected() {
        assertEquals(" ", LogResource.normalizePlannedDay(null));
        assertEquals(" ", LogResource.normalizePlannedDay("  "));
        assertEquals("Tuesday", LogResource.normalizePlannedDay("tuesday"));
        assertEquals("Sunday", LogResource.normalizePlannedDay(" SUNDAY "));
        assertNull(LogResource.normalizePlannedDay("Funday"));
        assertNull(LogResource.normalizePlannedDay("Monday; DROP TABLE"));
    }

    @Test
    void restMustBeABoolean() {
        assertEquals(" ", LogResource.normalizeRest(null));
        assertEquals("true", LogResource.normalizeRest("TRUE"));
        assertEquals("false", LogResource.normalizeRest("false"));
        assertNull(LogResource.normalizeRest("maybe"));
    }

    @Test
    void oversizedFieldsAreDetected() {
        assertFalse(LogResource.tooLarge(null, 10));
        assertFalse(LogResource.tooLarge("0123456789", 10));
        assertTrue(LogResource.tooLarge("01234567890", 10));
    }
}
