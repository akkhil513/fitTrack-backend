package com.fitTrack.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.Map;

@Data
public class FitLog {
    private String userId;
    private String date;
    private int dayNumber;
    private String session;
    private String exercises;
    private String protein;
    private String calories;
    private String water;
    private String sleep;
    private String notes;
    private Map<String, String> workout;
    private Map<String, String> nutrition;
    /** Daily checklist. Accepted as an object or as a JSON string; returned as the stored JSON string. */
    private JsonNode checklist;
    private String checklistJson;
    private String foodEntries;
    /** "true" when the user chose to skip this training day. */
    private String skipped;
    /** Weekday name of the plan session this log covers ("Tuesday"), blank when it is the calendar day's own session. */
    private String plannedDay;
    /** "true" when the user chose to rest on this date instead of training. */
    private String rest;
}