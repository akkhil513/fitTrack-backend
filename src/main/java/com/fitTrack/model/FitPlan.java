package com.fitTrack.model;

import lombok.Data;

@Data
public class FitPlan {

    private String userId;
    private String planId;
    private String createdAt;
    private String strategy;
    private String training;
    private String nutrition;
    private String supplements;
    private String recovery;
    private String status;
    private String dailyChecklist;
    private int weekNumber;
    /** Onboarding answers as sent to the model, kept so weekly regeneration knows the user's goal and limits. */
    private String profile;
    /** JSON summary of the week that just finished (see WeekReport); blank for week 1. */
    private String weekSummary;
}
