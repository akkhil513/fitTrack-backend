package com.fitTrack.mapper;

import com.fitTrack.model.UserProfile;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import java.util.HashMap;
import java.util.Map;

public class UserMapper {

    private static String nullSafe(String val) {
        return val != null ? val : " ";
    }

    public static UserProfile toUser(Map<String, AttributeValue> item) {
        UserProfile user = new UserProfile();
        user.setUserId(item.get("userId").s());
        user.setFirstName(item.get("firstName").s());
        user.setLastName(item.get("lastName").s());
        user.setEmail(item.get("email").s());
        user.setStartDate(item.get("startDate").s());
        user.setUsername(item.getOrDefault("username", AttributeValue.fromS("")).s());
        user.setMeasurements(item.getOrDefault("measurements", AttributeValue.fromS(" ")).s());
        user.setMeasurementHistory(item.getOrDefault("measurementHistory", AttributeValue.fromS(" ")).s());
        user.setMealTemplates(item.getOrDefault("mealTemplates", AttributeValue.fromS(" ")).s());
        user.setPhotoUrl(item.getOrDefault("photoUrl", AttributeValue.fromS(" ")).s());
        return user;
    }

    public static Map<String, AttributeValue> toMap(UserProfile user) {
        Map<String, AttributeValue> map = new HashMap<>();
        map.put("userId",             AttributeValue.fromS(nullSafe(user.getUserId())));
        map.put("firstName",          AttributeValue.fromS(nullSafe(user.getFirstName())));
        map.put("lastName",           AttributeValue.fromS(nullSafe(user.getLastName())));
        map.put("email",              AttributeValue.fromS(nullSafe(user.getEmail())));
        map.put("startDate",          AttributeValue.fromS(java.time.LocalDate.now().toString()));
        map.put("username",           AttributeValue.fromS(user.getUsername() != null && !user.getUsername().isEmpty() ? user.getUsername() : " "));
        map.put("measurements",       AttributeValue.fromS(nullSafe(user.getMeasurements())));
        map.put("measurementHistory", AttributeValue.fromS(nullSafe(user.getMeasurementHistory())));
        map.put("mealTemplates",      AttributeValue.fromS(nullSafe(user.getMealTemplates())));
        map.put("photoUrl",           AttributeValue.fromS(nullSafe(user.getPhotoUrl())));
        return map;
    }
}