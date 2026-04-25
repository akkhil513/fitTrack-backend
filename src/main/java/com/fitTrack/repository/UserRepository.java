package com.fitTrack.repository;

import jakarta.enterprise.context.ApplicationScoped;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.Map;

@ApplicationScoped
public class UserRepository {

    private final DynamoDbClient dynamoDB = DynamoDbClient.builder()
            .region(Region.US_EAST_1)
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();

    private static final String TABLE = "fittrack-users";

    public void saveUser(Map<String, AttributeValue> item) {
        dynamoDB.putItem(PutItemRequest.builder()
                .tableName(TABLE)
                .item(item)
                .build());
    }

    public Map<String, AttributeValue> getUser(String userId) {
        return dynamoDB.getItem(GetItemRequest.builder()
                .tableName(TABLE)
                .key(Map.of("userId", AttributeValue.fromS(userId)))
                .build()).item();
    }

    public void updateUser(String userId, String firstName, String lastName, String startDate) {
        dynamoDB.updateItem(UpdateItemRequest.builder()
                .tableName(TABLE)
                .key(Map.of("userId", AttributeValue.fromS(userId)))
                .updateExpression("SET firstName = :fn, lastName = :ln, startDate = :sd")
                .expressionAttributeValues(Map.of(
                        ":fn", AttributeValue.fromS(firstName != null ? firstName : " "),
                        ":ln", AttributeValue.fromS(lastName != null ? lastName : " "),
                        ":sd", AttributeValue.fromS(startDate != null ? startDate : " ")
                )).build());
    }

    public boolean isUsernameTaken(String username) {
        var result = dynamoDB.query(QueryRequest.builder()
                .tableName(TABLE)
                .indexName("username-index")
                .keyConditionExpression("username = :u")
                .expressionAttributeValues(Map.of(
                        ":u", AttributeValue.fromS(username)
                ))
                .build());
        return !result.items().isEmpty();
    }

    public void updateMeasurements(String userId, String measurements, String measurementHistory) {
        dynamoDB.updateItem(UpdateItemRequest.builder()
                .tableName(TABLE)
                .key(Map.of("userId", AttributeValue.fromS(userId)))
                .updateExpression("SET measurements = :m, measurementHistory = :h")
                .expressionAttributeValues(Map.of(
                        ":m", AttributeValue.fromS(measurements),
                        ":h", AttributeValue.fromS(measurementHistory)
                )).build());
    }

    public void updateMealTemplates(String userId, String mealTemplates) {
        dynamoDB.updateItem(UpdateItemRequest.builder()
                .tableName(TABLE)
                .key(Map.of("userId", AttributeValue.fromS(userId)))
                .updateExpression("SET mealTemplates = :m")
                .expressionAttributeValues(Map.of(
                        ":m", AttributeValue.fromS(mealTemplates != null ? mealTemplates : " ")
                ))
                .build());
    }
}