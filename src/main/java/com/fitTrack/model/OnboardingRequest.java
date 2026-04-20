package com.fitTrack.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OnboardingRequest {
    private String userId;
    private String age;
    private String gender;
    private String height;
    private String weight;
    private String physique;
    private List<String> fatStorage;
    private String primaryGoal;
    private List<String> laggingMuscles;
    private String trainingLevel;
    private String gymAccess;
    private String daysPerWeek;
    private String sessionDuration;
    private String sleepHours;
    private String dietType;
    private String foodPreference;
    private String appetite;
    private List<String> supplements;
    private List<String> injuries;
    private List<String> visualGoals;
    private String bulkApproach;
    private String coachingStyle;
    private String trackingPreference;
    private String preferredTrainTime;
    private String stressLevel;
}