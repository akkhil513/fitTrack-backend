package com.fitTrack.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeekReportTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void summarisesSessionsAndSortsChangesByName() throws Exception {
        Map<String, ProgressionCalculator.Suggestion> weights = new LinkedHashMap<>();
        weights.put("squat", new ProgressionCalculator.Suggestion("Squat", 102.5, ProgressionCalculator.Action.INCREASE));
        weights.put("bench", new ProgressionCalculator.Suggestion("bench press", 60, ProgressionCalculator.Action.HOLD));

        JsonNode json = MAPPER.readTree(WeekReport.toJson(2, 4, weights));
        assertEquals(2, json.get("week").asInt());
        assertEquals(4, json.get("sessions").asInt());
        assertEquals(2, json.get("changes").size());
        assertEquals("bench press", json.get("changes").get(0).get("name").asText());
        assertEquals("HOLD", json.get("changes").get(0).get("action").asText());
        assertEquals("Squat", json.get("changes").get(1).get("name").asText());
        assertEquals("INCREASE", json.get("changes").get(1).get("action").asText());
    }

    @Test
    void emptyAndNullInputsStillProduceValidJson() throws Exception {
        JsonNode json = MAPPER.readTree(WeekReport.toJson(1, -3, null));
        assertEquals(0, json.get("sessions").asInt());
        assertEquals(0, json.get("changes").size());
    }

    @Test
    void capsTheNumberOfChanges() throws Exception {
        Map<String, ProgressionCalculator.Suggestion> weights = new LinkedHashMap<>();
        for (int i = 0; i < 30; i++) {
            weights.put("e" + i, new ProgressionCalculator.Suggestion("E" + i, 10 + i, ProgressionCalculator.Action.HOLD));
        }
        assertEquals(WeekReport.MAX_CHANGES, MAPPER.readTree(WeekReport.toJson(1, 3, weights)).get("changes").size());
    }
}
