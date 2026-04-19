package com.fitTrack.repository;

import jakarta.enterprise.context.ApplicationScoped;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.Map;

@ApplicationScoped
public class PlanRepository {

    private final DynamoDbClient dynamoDB = DynamoDbClient.builder()
            .region(Region.US_EAST_1)
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();

    private static final String TABLE = "fittrack-plans";

    // Saves AI generated plan — one plan per user (userId is the key)
    public void savePlan(Map<String, AttributeValue> item) {
        dynamoDB.putItem(PutItemRequest.builder()
                .tableName(TABLE)
                .item(item)
                .build());
    }

    // Fetches plan by userId
    public Map<String, AttributeValue> getPlan(String userId) {
        return dynamoDB.getItem(GetItemRequest.builder()
                .tableName(TABLE)
                .key(Map.of("userId", AttributeValue.fromS(userId)))
                .build()).item();
    }
}