package com.fitTrack.service;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeekSummaryTest {

    private static Map<String, AttributeValue> log(String date, String exercises, String protein, String calories, String checklist) {
        Map<String, AttributeValue> m = new HashMap<>();
        m.put("date", AttributeValue.fromS(date));
        m.put("session", AttributeValue.fromS("Push A"));
        m.put("exercises", AttributeValue.fromS(exercises));
        m.put("protein", AttributeValue.fromS(protein));
        m.put("calories", AttributeValue.fromS(calories));
        m.put("water", AttributeValue.fromS(" "));
        m.put("checklist", AttributeValue.fromS(checklist));
        return m;
    }

    private static final String DONE = "[{\"name\":\"Bench\",\"sets\":[{\"weight\":\"135\",\"reps\":\"8\",\"done\":true}]}]";

    @Test
    void countsOnlyDaysWithCompletedSetsAsSessions() {
        var d = WeekSummary.build(1, List.of(
                log("2026-11-09", DONE, "150", "2400", "{\"a\":true,\"b\":false}"),
                log("2026-11-10", " ", "120", "2000", "{\"a\":true,\"b\":true}")), Map.of());
        assertEquals(1, d.sessions());
        assertEquals(1, d.exerciseJson().size());
    }

    @Test
    void averagesNutritionOverLoggedDaysOnly() {
        var d = WeekSummary.build(1, List.of(
                log("2026-11-09", DONE, "150", "2400", "{}"),
                log("2026-11-10", " ", "100", "2000", "{}"),
                log("2026-11-11", " ", " ", " ", "{}")), Map.of());
        assertTrue(d.text().contains("Average protein: 125 g/day"));
        assertTrue(d.text().contains("Average calories: 2200 kcal/day"));
    }

    @Test
    void reportsChecklistCompletion() {
        var d = WeekSummary.build(1, List.of(
                log("2026-11-09", DONE, "0", "0", "{\"a\":true,\"b\":false}")), Map.of());
        assertTrue(d.text().contains("Daily checklist completion: 50%"));
    }

    @Test
    void handlesAnEmptyWeek() {
        var d = WeekSummary.build(2, List.of(), Map.of());
        assertEquals(0, d.sessions());
        assertTrue(d.text().contains("No workouts logged."));
        assertTrue(d.text().contains("No food logged."));
    }

    @Test
    void listsFixedWeightsForTheModel() {
        var weights = Map.of("bench", new ProgressionCalculator.Suggestion("Bench", 140, ProgressionCalculator.Action.INCREASE));
        var d = WeekSummary.build(1, List.of(), weights);
        assertTrue(d.text().contains("- Bench: 140 (INCREASE)"));
    }
}
