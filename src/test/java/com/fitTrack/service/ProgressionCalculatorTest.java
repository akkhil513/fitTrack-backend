package com.fitTrack.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ProgressionCalculatorTest {

    private static final ObjectMapper M = new ObjectMapper();

    private static JsonNode plan(String name, int sets, String reps, String target) throws Exception {
        return M.readTree("""
                {"Monday":{"isRestDay":false,"exercises":[
                  {"name":"%s","sets":%d,"reps":"%s","rest":"90s","targetWeight":"%s"}]}}
                """.formatted(name, sets, reps, target));
    }

    private static String log(String name, String... sets) {
        StringBuilder sb = new StringBuilder("[{\"name\":\"" + name + "\",\"sets\":[");
        for (int i = 0; i < sets.length; i++) {
            String[] p = sets[i].split("x");
            sb.append(i > 0 ? "," : "").append("{\"weight\":\"").append(p[0]).append("\",\"reps\":\"")
                    .append(p[1]).append("\",\"done\":").append(!p[1].equals("0")).append("}");
        }
        return sb.append("]}]").toString();
    }

    @Test
    void increasesUpperBodyByFiveWhenEverySetHitsTopOfRange() throws Exception {
        var out = ProgressionCalculator.nextWeights(plan("Bench Press", 3, "8-10", "135"),
                List.of(log("Bench Press", "135x10", "135x10", "135x10")), 3);
        assertEquals(140.0, out.get("bench press").weight());
        assertEquals(ProgressionCalculator.Action.INCREASE, out.get("bench press").action());
    }

    @Test
    void increasesLowerBodyByTen() throws Exception {
        var out = ProgressionCalculator.nextWeights(plan("Back Squat", 3, "5", "225"),
                List.of(log("Back Squat", "225x5", "225x5", "225x5")), 4);
        assertEquals(235.0, out.get("back squat").weight());
    }

    @Test
    void holdsWhenAnySetMissesTheTopOfTheRange() throws Exception {
        var out = ProgressionCalculator.nextWeights(plan("Bench Press", 3, "8-10", "135"),
                List.of(log("Bench Press", "135x10", "135x9", "135x8")), 4);
        assertEquals(135.0, out.get("bench press").weight());
        assertEquals(ProgressionCalculator.Action.HOLD, out.get("bench press").action());
    }

    @Test
    void holdsWhenFewerSetsWereCompletedThanPlanned() throws Exception {
        var out = ProgressionCalculator.nextWeights(plan("Bench Press", 4, "8-10", "135"),
                List.of(log("Bench Press", "135x10", "135x10", "135x10")), 4);
        assertEquals(ProgressionCalculator.Action.HOLD, out.get("bench press").action());
    }

    @Test
    void neverIncreasesWithFewerThanThreeSessions() throws Exception {
        var out = ProgressionCalculator.nextWeights(plan("Bench Press", 3, "8-10", "135"),
                List.of(log("Bench Press", "135x10", "135x10", "135x10")), 2);
        assertEquals(135.0, out.get("bench press").weight());
    }

    @Test
    void usesTheHeaviestWorkingWeightLogged() throws Exception {
        var out = ProgressionCalculator.nextWeights(plan("Bench Press", 3, "8-10", "135"),
                List.of(log("Bench Press", "145x10", "145x10", "145x10")), 3);
        assertEquals(150.0, out.get("bench press").weight());
    }

    @Test
    void ignoresSetsNotMarkedDone() throws Exception {
        var out = ProgressionCalculator.nextWeights(plan("Bench Press", 3, "8-10", "135"),
                List.of(log("Bench Press", "135x10", "135x10", "200x0")), 3);
        assertEquals(135.0, out.get("bench press").weight());
    }

    @Test
    void keepsPreviousTargetWhenNothingWasLogged() throws Exception {
        var out = ProgressionCalculator.nextWeights(plan("Bench Press", 3, "8-10", "135"), List.of(), 0);
        assertEquals(135.0, out.get("bench press").weight());
    }

    @Test
    void omitsExercisesWithNoDataAndNoTarget() throws Exception {
        var out = ProgressionCalculator.nextWeights(plan("Plank", 3, "30s", ""), List.of(), 0);
        assertFalse(out.containsKey("plank"));
    }

    @Test
    void timedOrAmrapRepsAreNeverProgressed() throws Exception {
        var out = ProgressionCalculator.nextWeights(plan("Farmer Walk", 3, "AMRAP", "50"),
                List.of(log("Farmer Walk", "50x20", "50x20", "50x20")), 4);
        assertEquals(ProgressionCalculator.Action.HOLD, out.get("farmer walk").action());
    }

    @Test
    void skipsMalformedLogDaysWithoutFailing() throws Exception {
        var out = ProgressionCalculator.nextWeights(plan("Bench Press", 3, "8-10", "135"),
                List.of("not json", log("Bench Press", "135x10", "135x10", "135x10")), 3);
        assertEquals(140.0, out.get("bench press").weight());
    }

    @Test
    void formatsWholeAndHalfPounds() {
        assertEquals("145", ProgressionCalculator.format(145.0));
        assertEquals("142.5", ProgressionCalculator.format(142.5));
    }

    @Test
    void exercisesAreMatchedCaseInsensitively() throws Exception {
        Map<String, ProgressionCalculator.Suggestion> out = ProgressionCalculator.nextWeights(
                plan("Barbell Row", 3, "8-10", "100"),
                List.of(log("barbell row", "100x10", "100x10", "100x10")), 3);
        assertEquals(105.0, out.get("barbell row").weight());
    }
}
