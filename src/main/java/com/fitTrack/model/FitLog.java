package com.fitTrack.model;

import lombok.Data;

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
}