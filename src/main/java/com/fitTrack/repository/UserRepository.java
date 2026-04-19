package com.fitTrack.repository;

import jakarta.enterprise.context.ApplicationScoped;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

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

    public void updateUser(String userId, String firstName, String lastName) {
        dynamoDB.updateItem(UpdateItemRequest.builder()
                .tableName(TABLE)
                .key(Map.of("userId", AttributeValue.fromS(userId)))
                .updateExpression("SET firstName = :fn, lastName =:ln")
                .expressionAttributeValues(Map.of(
                        ":fn", AttributeValue.fromS(firstName),
                        ":ln", AttributeValue.fromS(lastName)
                )).build());

    }
}