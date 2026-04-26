package com.fitTrack.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class ClaudeService {

    @ConfigProperty(name = "fittrack.claude.model")
    String model;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private static final SecretsManagerClient secretsClient = SecretsManagerClient.builder()
            .region(Region.US_EAST_1)
            .build();

    private static String cachedApiKey = null;

    private String getApiKey() {
        if (cachedApiKey != null) return cachedApiKey;
        try {
            String secret = secretsClient.getSecretValue(
                    GetSecretValueRequest.builder()
                            .secretId("fittrack/anthropic-api-key")
                            .build()
            ).secretString();
            cachedApiKey = new ObjectMapper().readTree(secret).path("ANTHROPIC_API_KEY").asText();
            if (cachedApiKey == null || cachedApiKey.isEmpty()) {
                throw new RuntimeException("ANTHROPIC_API_KEY is empty in Secrets Manager");
            }
            return cachedApiKey;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get API key: " + e.getMessage(), e);
        }
    }

    public String generatePlan(String userProfile) throws Exception {

        String jsonBody = getRequestString(userProfile);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.anthropic.com/v1/messages"))
                .header("Content-Type", "application/json")
                .header("x-api-key", getApiKey())
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Claude API error: " + response.statusCode() + " " + response.body());
        }

        return response.body();
    }

    private String getRequestString(String userProfile) throws JsonProcessingException {

        Map<String, Object> exerciseItem = new java.util.LinkedHashMap<>();
        exerciseItem.put("type", "object");
        exerciseItem.put("required", List.of("name", "sets", "reps", "rest"));
        exerciseItem.put("properties", Map.of(
                "name", Map.of("type", "string"),
                "sets", Map.of("type", "integer"),
                "reps", Map.of("type", "string"),
                "rest", Map.of("type", "string")
        ));

        Map<String, Object> daySchema = new java.util.LinkedHashMap<>();
        daySchema.put("type", "object");
        daySchema.put("required", List.of("session", "exercises"));
        daySchema.put("properties", Map.of(
                "session", Map.of("type", "string"),
                "isRestDay", Map.of("type", "boolean"),
                "exercises", Map.of(
                        "type", "array",
                        "items", exerciseItem
                )
        ));

        Map<String, Object> trainingProperties = new java.util.LinkedHashMap<>();
        for (String day : List.of("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")) {
            trainingProperties.put(day, daySchema);
        }

        Map<String, Object> checklistItem = Map.of(
                "type", "object",
                "required", List.of("id", "label", "category", "time"),
                "properties", Map.of(
                        "id", Map.of("type", "string"),
                        "label", Map.of("type", "string"),
                        "category", Map.of("type", "string", "enum", List.of("supplement", "nutrition", "training", "recovery")),
                        "time", Map.of("type", "string")
                )
        );

        Map<String, Object> inputSchemaProperties = new java.util.LinkedHashMap<>();
        inputSchemaProperties.put("strategy",       Map.of("type", "string"));
        inputSchemaProperties.put("training",        Map.of("type", "object", "properties", trainingProperties));
        inputSchemaProperties.put("nutrition",       Map.of("type", "string"));
        inputSchemaProperties.put("supplements",     Map.of("type", "string"));
        inputSchemaProperties.put("recovery",        Map.of("type", "string"));
        inputSchemaProperties.put("dailyChecklist",  Map.of("type", "array", "items", checklistItem));

        Map<String, Object> inputSchema = new java.util.LinkedHashMap<>();
        inputSchema.put("type", "object");
        inputSchema.put("required", List.of("strategy", "training", "nutrition", "supplements", "recovery", "dailyChecklist"));
        inputSchema.put("properties", inputSchemaProperties);

        Map<String, Object> tool = Map.of(
                "name", "generate_fitness_plan",
                "description", "Generate a personalized fitness plan",
                "input_schema", inputSchema
        );

        Map<String, Object> requestBody = new java.util.LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", 4096);
        requestBody.put("system",
                "You are an elite fitness coach with 15+ years experience. " +
                "Generate evidence-based, progressive overload focused plans. " +
                "For supplements: be specific based on goal, diet, stress, current supplements. " +
                "If user is restocking protein powder, recommend best type with brands and dosage. " +
                "For nutrition: include specific calorie and protein targets based on weight and goal. " +
                "For dailyChecklist: generate 12-15 daily actionable items the user must complete every day. " +
                "Include items from all 4 categories: supplement (taking each supplement at right time), " +
                "nutrition (hitting protein/calorie targets, water intake, meal timing), " +
                "training (complete workout, log sets/reps), " +
                "recovery (sleep hours, stress management, steps, foam rolling). " +
                "Each item should have a specific time (Morning, Pre-workout, Post-workout, With lunch, Evening, Before bed, Daily). " +
                "Keep strategy, nutrition, supplements, recovery under 500 chars each."
        );
        requestBody.put("tools", List.of(tool));
        requestBody.put("tool_choice", Map.of("type", "tool", "name", "generate_fitness_plan"));
        requestBody.put("messages", List.of(
                Map.of("role", "user", "content",
                        "<user_profile>" + userProfile + "</user_profile>\n\nGenerate the plan.")
        ));

        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(requestBody);
    }

    private String callClaude(String requestBody) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.anthropic.com/v1/messages"))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", getApiKey())
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Claude API error: " + response.statusCode() + " " + response.body());
            }
            return response.body();
        } catch (Exception e) {
            throw new RuntimeException("Failed to call Claude API: " + e.getClass().getName() + ": " + e.getMessage(), e);
        }
    }

    public String calculateMealMacros(String mealDescription) throws Exception {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_tokens", 500,
                "system", "You are a nutrition expert. Analyze the meal and return ONLY a JSON object with: protein (number), calories (number), carbs (number), fat (number), breakdown (array of {name, protein, calories, carbs, fat}). No markdown, no explanation, just JSON.",
                "messages", new Object[]{
                        Map.of("role", "user", "content", "Calculate macros for: " + mealDescription)
                }
        );

        ObjectMapper mapper = new ObjectMapper();
        String jsonBody = mapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.anthropic.com/v1/messages"))
                .header("Content-Type", "application/json")
                .header("x-api-key", getApiKey())
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Claude API error: " + response.statusCode() + " " + response.body());
        }

        return response.body();
    }
}