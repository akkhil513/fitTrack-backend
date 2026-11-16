package com.fitTrack.service;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WeeklyPlanServiceTest {

    private static final LocalDate START = LocalDate.of(2026, 11, 2);

    @Test
    void firstSevenDaysAreWeekOne() {
        assertEquals(1, WeeklyPlanService.weekFor(START, START));
        assertEquals(1, WeeklyPlanService.weekFor(START, START.plusDays(6)));
    }

    @Test
    void weekRollsOverEverySevenDays() {
        assertEquals(2, WeeklyPlanService.weekFor(START, START.plusDays(7)));
        assertEquals(7, WeeklyPlanService.weekFor(START, START.plusDays(42)));
    }

    @Test
    void futureStartDateNeverGoesBelowWeekOne() {
        assertEquals(1, WeeklyPlanService.weekFor(START, START.minusDays(3)));
        assertEquals(1, WeeklyPlanService.weekFor(START, START.minusDays(30)));
    }

    @Test
    void storedWeekZeroIsTreatedAsWeekOne() {
        assertEquals(1, WeeklyPlanService.normalizeStoredWeek(0));
        assertEquals(1, WeeklyPlanService.normalizeStoredWeek(-2));
        assertEquals(4, WeeklyPlanService.normalizeStoredWeek(4));
    }

    @Test
    void computedWeightsOverrideTheModelsNumbers() throws Exception {
        ObjectNode training = (ObjectNode) new ObjectMapper().readTree(
                "{\"Monday\":{\"exercises\":[{\"name\":\"Bench Press\",\"targetWeight\":\"999\"},{\"name\":\"New Move\",\"targetWeight\":\"\"}]}}");
        WeeklyPlanService.applyWeights(training, Map.of("bench press",
                new ProgressionCalculator.Suggestion("Bench Press", 140, ProgressionCalculator.Action.INCREASE)));
        var ex = training.path("Monday").path("exercises");
        assertEquals("140", ex.get(0).path("targetWeight").asText());
        assertEquals("", ex.get(1).path("targetWeight").asText());
    }

    @Test
    void extractsJsonFromFencedModelOutput() throws Exception {
        String body = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"```json\\n{\\\"a\\\":1}\\n```\"}]}}]}";
        assertEquals("{\"a\":1}", WeeklyPlanService.extractJsonText(body));
    }

    @Test
    void emptyModelResponseIsAnError() {
        assertThrows(IllegalStateException.class, () -> WeeklyPlanService.extractJsonText("{\"candidates\":[]}"));
    }
}
