package com.fitTrack.repository;

import jakarta.enterprise.context.ApplicationScoped;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.List;
import java.util.Map;

@ApplicationScoped
public class LogRepository {

    private final DynamoDbClient dynamoDB = DynamoDbClient.builder()
            .region(Region.US_EAST_1)
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();

    private static final String TABLE = "fittrack-logs";

    public void saveLog(Map<String, AttributeValue> item) {
        dynamoDB.putItem(PutItemRequest.builder()
                .tableName(TABLE)
                .item(item)
                .build());
    }

    public Map<String, AttributeValue> getLog(String userId, String date) {
        return dynamoDB.getItem(GetItemRequest.builder()
                .tableName(TABLE)
                .key(Map.of(
                        "userId", AttributeValue.fromS(userId),
                        "date",   AttributeValue.fromS(date)
                ))
                .build()).item();
    }

    public List<Map<String, AttributeValue>> getLogs(String userId) {
        return dynamoDB.query(QueryRequest.builder()
                .tableName(TABLE)
                .keyConditionExpression("userId = :uid")
                .expressionAttributeValues(Map.of(
                        ":uid", AttributeValue.fromS(userId)
                ))
                .build()).items();
    }

    public void updateLog(String userId, String date, Map<String, AttributeValue> values) {
        dynamoDB.updateItem(UpdateItemRequest.builder()
                .tableName(TABLE)
                .key(Map.of(
                        "userId", AttributeValue.fromS(userId),
                        "date",   AttributeValue.fromS(date)
                ))
                .updateExpression("SET #s = :s, exercises = :e, protein = :p, calories = :c, water = :w, sleep = :sl, notes = :n, checklist = :cl")
                .expressionAttributeNames(Map.of("#s", "session"))
                .expressionAttributeValues(values)
                .build());
    }
}