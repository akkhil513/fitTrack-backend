package com.fitTrack.mapper;

import com.fitTrack.model.UserProfile;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import java.util.Map;

public class UserMapper {

    public static UserProfile toUser(Map<String, AttributeValue> item) {
        UserProfile user = new UserProfile();
        user.setUserId(item.get("userId").s());
        user.setFirstName(item.get("firstName").s());
        user.setLastName(item.get("lastName").s());
        user.setEmail(item.get("email").s());
        user.setStartDate(item.get("startDate").s());
        user.setUsername(item.getOrDefault("username", AttributeValue.fromS("")).s());
        user.setMeasurements(item.getOrDefault("measurements", AttributeValue.fromS(" ")).s());
        return user;
    }

    public static Map<String, AttributeValue> toMap(UserProfile user) {
        return Map.of(
                "userId",    AttributeValue.fromS(user.getUserId()),
                "firstName", AttributeValue.fromS(user.getFirstName()),
                "lastName",  AttributeValue.fromS(user.getLastName()),
                "email",     AttributeValue.fromS(user.getEmail()),
                "startDate", AttributeValue.fromS(java.time.LocalDate.now().toString()),
                "username",  AttributeValue.fromS(user.getUsername() != null && !user.getUsername().isEmpty() ? user.getUsername() : " "),
                "measurements", AttributeValue.fromS(user.getMeasurements() != null ? user.getMeasurements() : " ")
        );
    }
}