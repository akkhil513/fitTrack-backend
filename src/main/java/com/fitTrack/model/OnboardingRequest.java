package com.fitTrack.model;

import lombok.Data;

@Data
public class OnboardingRequest {
    private String userId;
    private String age;
    private String gender;
    private String height;
    private String weight;
    private String physique;
    private String fatStorage;
    private String primaryGoal;
    private String trainingLevel;
    private String gymAccess;
    private String daysPerWeek;
    private String sessionDuration;
    private String dietType;
    private String foodPreference;
    private String appetite;
    private String supplements;
    private String injuries;
    private String sleepHours;
    private String stressLevel;
    private String coachingStyle;
    private String bulkApproach;
    private String visualGoals;
}