package com.fitTrack.repository;

import jakarta.enterprise.context.ApplicationScoped;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.ArrayList;
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

    /** All logs for a user with {@code from <= date <= to} (ISO dates, inclusive), across result pages. */
    public List<Map<String, AttributeValue>> getLogsBetween(String userId, String from, String to) {
        List<Map<String, AttributeValue>> out = new ArrayList<>();
        Map<String, AttributeValue> lastKey = null;
        do {
            QueryRequest.Builder request = QueryRequest.builder()
                    .tableName(TABLE)
                    .keyConditionExpression("userId = :uid AND #d BETWEEN :from AND :to")
                    .expressionAttributeNames(Map.of("#d", "date"))
                    .expressionAttributeValues(Map.of(
                            ":uid", AttributeValue.fromS(userId),
                            ":from", AttributeValue.fromS(from),
                            ":to", AttributeValue.fromS(to)));
            if (lastKey != null) {
                request.exclusiveStartKey(lastKey);
            }
            QueryResponse page = dynamoDB.query(request.build());
            out.addAll(page.items());
            lastKey = page.hasLastEvaluatedKey() && !page.lastEvaluatedKey().isEmpty() ? page.lastEvaluatedKey() : null;
        } while (lastKey != null);
        return out;
    }

    /** Sets only the skipped flag on an existing log; every other field is left alone. */
    public void setSkipped(String userId, String date, boolean skipped) {
        dynamoDB.updateItem(UpdateItemRequest.builder()
                .tableName(TABLE)
                .key(Map.of(
                        "userId", AttributeValue.fromS(userId),
                        "date",   AttributeValue.fromS(date)
                ))
                .updateExpression("SET skipped = :sk")
                .conditionExpression("attribute_exists(userId)")
                .expressionAttributeValues(Map.of(":sk", AttributeValue.fromS(skipped ? "true" : "false")))
                .build());
    }

    public void updateLog(String userId, String date, Map<String, AttributeValue> values) {
        dynamoDB.updateItem(UpdateItemRequest.builder()
                .tableName(TABLE)
                .key(Map.of(
                        "userId", AttributeValue.fromS(userId),
                        "date",   AttributeValue.fromS(date)
                ))
                .updateExpression("SET #s = :s, exercises = :e, protein = :p, calories = :c, water = :w, sleep = :sl, notes = :n, checklist = :cl, foodEntries = :fe, plannedDay = :pd, #r = :rs")
                .expressionAttributeNames(Map.of("#s", "session", "#r", "rest"))
                .expressionAttributeValues(values)
                .build());
    }
}