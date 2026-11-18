package com.fitTrack.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionsClearedTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 16);
    private static final String DONE = "[{\"name\":\"Squat\",\"sets\":[{\"weight\":\"60\",\"reps\":\"5\",\"done\":true}]}]";

    private static JsonNode plan() throws Exception {
        String session = "{\"isRestDay\":false,\"exercises\":[{\"name\":\"Squat\"}]}";
        return new ObjectMapper().readTree(
                "{\"Monday\":" + session + ",\"Tuesday\":" + session + ",\"Wednesday\":{\"isRestDay\":true,\"exercises\":[]}}");
    }

    private static Map<String, AttributeValue> log(String date, String plannedDay, String rest, String exercises) {
        return Map.of(
                "date", AttributeValue.fromS(date),
                "plannedDay", AttributeValue.fromS(plannedDay),
                "rest", AttributeValue.fromS(rest),
                "exercises", AttributeValue.fromS(exercises),
                "skipped", AttributeValue.fromS(" "));
    }

    @Test
    void sessionsLoggedOnTheirOwnDatesCountWhenNoPlannedDay() throws Exception {
        var logs = List.of(log("2026-11-16", " ", " ", DONE), log("2026-11-17", " ", " ", DONE));
        assertTrue(WeeklyPlanService.allSessionsCleared(plan(), MONDAY, logs));
    }

    @Test
    void aMissingSessionKeepsTheWeekOpen() throws Exception {
        var logs = List.of(log("2026-11-17", " ", " ", DONE));
        assertFalse(WeeklyPlanService.allSessionsCleared(plan(), MONDAY, logs));
    }

    @Test
    void restPushesMondaysSessionToTuesday() throws Exception {
        var logs = List.of(
                log("2026-11-16", " ", "true", " "),
                log("2026-11-17", "Monday", " ", DONE),
                log("2026-11-18", "Tuesday", " ", DONE));
        assertTrue(WeeklyPlanService.allSessionsCleared(plan(), MONDAY, logs));
    }

    @Test
    void aRestLogNeverCoversASession() throws Exception {
        var logs = List.of(log("2026-11-16", " ", "true", DONE), log("2026-11-17", " ", " ", DONE));
        assertFalse(WeeklyPlanService.allSessionsCleared(plan(), MONDAY, logs));
    }

    @Test
    void aPlanWithNoTrainingDaysIsNeverDone() throws Exception {
        JsonNode empty = new ObjectMapper().readTree("{\"Monday\":{\"isRestDay\":true,\"exercises\":[]}}");
        assertFalse(WeeklyPlanService.allSessionsCleared(empty, MONDAY, List.of()));
    }
}
