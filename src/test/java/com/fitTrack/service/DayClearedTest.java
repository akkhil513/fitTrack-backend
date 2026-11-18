package com.fitTrack.service;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DayClearedTest {

    private static final String DONE_SET = "[{\"name\":\"Squat\",\"sets\":[{\"weight\":\"60\",\"reps\":\"5\",\"done\":true}]}]";
    private static final String OPEN_SET = "[{\"name\":\"Squat\",\"sets\":[{\"weight\":\"60\",\"reps\":\"5\",\"done\":false}]}]";

    private static Map<String, AttributeValue> log(String exercises, String skipped) {
        return Map.of(
                "exercises", AttributeValue.fromS(exercises),
                "skipped", AttributeValue.fromS(skipped));
    }

    @Test
    void aCompletedSetClearsTheDay() {
        assertTrue(WeeklyPlanService.dayCleared(log(DONE_SET, " ")));
    }

    @Test
    void aSkippedDayIsCleared() {
        assertTrue(WeeklyPlanService.dayCleared(log(" ", "true")));
    }

    @Test
    void nothingDoneAndNotSkippedIsNotCleared() {
        assertFalse(WeeklyPlanService.dayCleared(log(OPEN_SET, " ")));
        assertFalse(WeeklyPlanService.dayCleared(log(" ", "false")));
        assertFalse(WeeklyPlanService.dayCleared(Map.of()));
        assertFalse(WeeklyPlanService.dayCleared(null));
    }
}
