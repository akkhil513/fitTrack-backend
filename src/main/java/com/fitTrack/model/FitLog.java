package com.fitTrack.model;

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
    private Map<String, String> checklist;
    private String checklistJson;
}