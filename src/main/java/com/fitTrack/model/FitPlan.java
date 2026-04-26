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
}
