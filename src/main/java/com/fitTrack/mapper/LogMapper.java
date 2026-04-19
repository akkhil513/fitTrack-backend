package com.fitTrack.mapper;

import com.fitTrack.model.FitLog;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;

public class LogMapper {

    public static Map<String, AttributeValue> toMap(FitLog log) {
        return Map.of(
                "userId",    AttributeValue.fromS(log.getUserId()),
                "date",      AttributeValue.fromS(log.getDate()),
                "dayNumber", AttributeValue.fromN(String.valueOf(log.getDayNumber())),
                "session",   AttributeValue.fromS(log.getSession()),
                "exercises", AttributeValue.fromS(log.getExercises()),
                "protein",   AttributeValue.fromS(log.getProtein()),
                "calories",  AttributeValue.fromS(log.getCalories()),
                "water",     AttributeValue.fromS(log.getWater()),
                "sleep",     AttributeValue.fromS(log.getSleep()),
                "notes",     AttributeValue.fromS(log.getNotes())
        );
    }

    public static FitLog toLog(Map<String, AttributeValue> item) {
        FitLog log = new FitLog();
        log.setUserId(item.get("userId").s());
        log.setDate(item.get("date").s());
        log.setDayNumber(Integer.parseInt(item.get("dayNumber").n()));
        log.setSession(item.get("session").s());
        log.setExercises(item.get("exercises").s());
        log.setProtein(item.get("protein").s());
        log.setCalories(item.get("calories").s());
        log.setWater(item.get("water").s());
        log.setSleep(item.get("sleep").s());
        log.setNotes(item.get("notes").s());
        return log;
    }
}