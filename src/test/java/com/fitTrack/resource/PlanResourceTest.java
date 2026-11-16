package com.fitTrack.resource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlanResourceTest {

    @Test
    void acceptsRealChallengeLengths() {
        assertEquals(45, PlanResource.parseDuration("45"));
        assertEquals(100, PlanResource.parseDuration(" 100 "));
    }

    @Test
    void rejectsMissingOrNonsenseValues() {
        assertNull(PlanResource.parseDuration(null));
        assertNull(PlanResource.parseDuration(""));
        assertNull(PlanResource.parseDuration("abc"));
        assertNull(PlanResource.parseDuration("0"));
        assertNull(PlanResource.parseDuration("5000"));
    }
}
