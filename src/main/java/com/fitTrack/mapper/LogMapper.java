package com.fitTrack.mapper;

import com.fitTrack.model.FitLog;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.HashMap;
import java.util.Map;

public class LogMapper {

    public static Map<String, AttributeValue> toMap(FitLog log) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("userId",    AttributeValue.fromS(log.getUserId()));
        item.put("date",      AttributeValue.fromS(log.getDate()));
        item.put("dayNumber", AttributeValue.fromN(String.valueOf(log.getDayNumber())));
        item.put("session",   AttributeValue.fromS(nullSafe(log.getSession())));
        item.put("exercises", AttributeValue.fromS(nullSafe(log.getExercises())));
        item.put("protein",   AttributeValue.fromS(nullSafe(log.getProtein())));
        item.put("calories",  AttributeValue.fromS(nullSafe(log.getCalories())));
        item.put("water",     AttributeValue.fromS(nullSafe(log.getWater())));
        item.put("sleep",     AttributeValue.fromS(nullSafe(log.getSleep())));
        item.put("notes",     AttributeValue.fromS(nullSafe(log.getNotes())));
        return item;
    }

    private static String nullSafe(String value) {
        return (value == null || value.isEmpty()) ? " " : value;
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