package com.fitTrack.model;

import lombok.Data;

@Data
public class UserProfile {

    public String userId;
    public String firstName;
    public String lastName;
    public String email;
    public String startDate;
    public boolean isPublic;
    private String username;
    private String measurements;
    private String measurementHistory;
    private String mealTemplates;
    private String photoUrl;
}
