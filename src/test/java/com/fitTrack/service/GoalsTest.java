package com.fitTrack.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GoalsTest {

    private static void rejects(String json) {
        assertThrows(IllegalArgumentException.class, () -> Goals.normalize(json), json);
    }

    @Test
    void blankMeansNoGoals() {
        assertEquals("{}", Goals.normalize(null));
        assertEquals("{}", Goals.normalize(" "));
        assertEquals("{}", Goals.normalize("{}"));
    }

    @Test
    void validGoalsRoundTrip() {
        assertEquals("{\"targetWeight\":75.5,\"targetWaist\":82.0,\"eventDate\":\"2026-12-30\"}",
                Goals.normalize("{\"targetWeight\":75.5,\"targetWaist\":82,\"eventDate\":\"2026-12-30\"}"));
    }

    @Test
    void everyFieldIsOptionalAndNullIsIgnored() {
        assertEquals("{\"targetWeight\":70.0}", Goals.normalize("{\"targetWeight\":70,\"targetWaist\":null}"));
    }

    @Test
    void unknownFieldsAreDropped() {
        assertEquals("{\"targetWeight\":70.0}", Goals.normalize("{\"targetWeight\":70,\"admin\":true}"));
    }

    @Test
    void roundsToOneDecimal() {
        assertEquals("{\"targetWeight\":70.3}", Goals.normalize("{\"targetWeight\":70.26}"));
    }

    @Test
    void rejectsOutOfRangeNumbers() {
        rejects("{\"targetWeight\":19}");
        rejects("{\"targetWeight\":401}");
        rejects("{\"targetWaist\":29}");
        rejects("{\"targetWaist\":251}");
        rejects("{\"targetWeight\":-5}");
    }

    @Test
    void rejectsNonNumbers() {
        rejects("{\"targetWeight\":\"70\"}");
        rejects("{\"targetWaist\":true}");
        rejects("{\"targetWeight\":[70]}");
    }

    @Test
    void rejectsBadDates() {
        rejects("{\"eventDate\":\"2026-13-01\"}");
        rejects("{\"eventDate\":\"2026-02-30\"}");
        rejects("{\"eventDate\":\"Dec 30\"}");
        rejects("{\"eventDate\":20261230}");
        rejects("{\"eventDate\":\"1999-01-01\"}");
        rejects("{\"eventDate\":\"2101-01-01\"}");
    }

    @Test
    void rejectsWrongShapeAndOversize() {
        rejects("[1]");
        rejects("\"x\"");
        rejects("{not json");
        rejects("{\"targetWeight\":70," + " ".repeat(Goals.MAX_RAW_LENGTH) + "}");
    }
}
