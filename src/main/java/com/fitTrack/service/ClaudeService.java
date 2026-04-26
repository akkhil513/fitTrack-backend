package com.fitTrack.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class ClaudeService {

    @ConfigProperty(name = "fittrack.claude.model")
    String model;

    @Inject
    ObjectMapper objectMapper;

    private static volatile String cachedApiKey;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private final SecretsManagerClient secretsClient = SecretsManagerClient.builder()
        .region(Region.US_EAST_1)
        .build();

    // Build tool schema once — not on every request
    private static final Map<String, Object> TOOL_DEFINITION = buildToolDefinition();

    private static Map<String, Object> buildToolDefinition() {
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
            "exercises", Map.of("type", "array", "items", exerciseItem)
        ));

        Map<String, Object> trainingProperties = new java.util.LinkedHashMap<>();
        for (String day : List.of("Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday")) {
            trainingProperties.put(day, daySchema);
        }

        Map<String, Object> checklistItem = Map.of(
            "type", "object",
            "required", List.of("id", "label", "category", "time"),
            "properties", Map.of(
                "id", Map.of("type", "string"),
                "label", Map.of("type", "string"),
                "category", Map.of("type", "string",
                    "enum", List.of("supplement", "nutrition", "training", "recovery")),
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
        inputSchema.put("required", List.of("strategy","training","nutrition","supplements","recovery","dailyChecklist"));
        inputSchema.put("properties", inputSchemaProperties);

        return Map.of(
            "name", "generate_fitness_plan",
            "description", "Generate a personalized fitness plan",
            "input_schema", inputSchema
        );
    }

    private static final String SYSTEM_PROMPT = """
        You generate evidence-based fitness plans. Output only the tool call.

        PRINCIPLES (apply these, don't restate them):
        - Hypertrophy: 10-20 hard sets/muscle/week. Strength: 3-6 reps at 80-90% 1RM.
        - Protein: 1.6-2.2 g/kg for muscle gain; 1.8-2.4 g/kg in deficit.
        - Calories: surplus 200-300 kcal (bulk), deficit 300-500 kcal (cut), maintenance otherwise.
        - Progressive overload via weight, reps, or RIR. Deload every 4-8 weeks.
        - Match exercises to reported equipment. If unspecified, assume dumbbells + bodyweight.
        - If experience unspecified, default to beginner volumes.
        - CRITICAL: Generate EXACTLY the number of training days the user specified. Never exceed it.
        - Back days: ALWAYS train biceps with back on the same day. Pull = Back + Biceps. Never separate them.
        - Follow Push/Pull/Legs structure where possible for the specified number of days.

        SAFETY:
        - Injury, medication, pregnancy, age <18 or >65 → add "consult physician" note in strategy.
        - Never recommend deficits >500 kcal/day.
        - No stimulants if user reports anxiety, insomnia, or hypertension.

        SUPPLEMENTS:
        - Recommend only: creatine monohydrate, whey/casein/plant protein, vitamin D, omega-3, magnesium glycinate.
        - For each: name, exact dose, timing, one-sentence rationale tied to user goal.
        - Skip anything user already takes.
        - If user is restocking protein powder, recommend best type (Isolate vs Concentrate) with 2-3 brand names.

        NUTRITION:
        - Give specific calorie target and protein grams computed from user weight + goal.
        - Note meal timing relevant to training time.

        DAILY CHECKLIST (12-15 items):
        - Cover all 4 categories: supplement, nutrition, training, recovery.
        - Every item must be specific and measurable:
          Bad: "Eat enough protein." Good: "Hit 160g protein by 8 PM."
          Bad: "Stay hydrated." Good: "Drink 3.5L water by evening."
          Bad: "Sleep well." Good: "Lights out by 11 PM, 7+ hours sleep."
        - Each item gets one of: Morning, Pre-workout, Post-workout, With lunch, Evening, Before bed, Daily.

        LIMITS: strategy, nutrition, supplements, recovery under 500 chars each.
        TONE: Direct, specific, no hype. Concrete instructions the user will actually follow.
        """;

    private String getApiKey() {
        String key = cachedApiKey;
        if (key != null) return key;
        synchronized (ClaudeService.class) {
            if (cachedApiKey != null) return cachedApiKey;
            try {
                var response = secretsClient.getSecretValue(
                    GetSecretValueRequest.builder()
                        .secretId("fittrack/anthropic-api-key")
                        .build()
                );
                cachedApiKey = objectMapper.readTree(response.secretString())
                    .path("ANTHROPIC_API_KEY").asText();
                return cachedApiKey;
            } catch (Exception e) {
                throw new RuntimeException("Failed to get API key: " + e.getMessage());
            }
        }
    }

    private String postToClaude(String jsonBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.anthropic.com/v1/messages"))
            .timeout(Duration.ofSeconds(120))
            .header("Content-Type", "application/json")
            .header("x-api-key", getApiKey())
            .header("anthropic-version", "2023-06-01")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 429 || response.statusCode() == 529 || response.statusCode() >= 500) {
            // Retry once after 2 seconds
            Thread.sleep(2000);
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        }

        if (response.statusCode() != 200) {
            throw new RuntimeException("Claude API error: " + response.statusCode() + " " + response.body());
        }

        // Check stop_reason
        JsonNode root = objectMapper.readTree(response.body());
        String stopReason = root.path("stop_reason").asText();
        if ("max_tokens".equals(stopReason)) {
            System.out.println("WARNING: Claude response was truncated at max_tokens");
        }

        // Log usage
        JsonNode usage = root.path("usage");
        System.out.println("Claude usage — input: " + usage.path("input_tokens").asInt() +
            " output: " + usage.path("output_tokens").asInt());

        return response.body();
    }

    public String generatePlan(String userProfile) throws Exception {
        // Sanitize to prevent prompt injection
        String safe = userProfile.replace("</user_profile>", "");

        Map<String, Object> requestBody = new java.util.LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", 8192);
        requestBody.put("system", SYSTEM_PROMPT);
        requestBody.put("tools", List.of(TOOL_DEFINITION));
        requestBody.put("tool_choice", Map.of("type", "tool", "name", "generate_fitness_plan"));
        requestBody.put("messages", List.of(
            Map.of("role", "user", "content",
                "<user_profile>" + safe + "</user_profile>\n\nGenerate the plan.")
        ));

        return postToClaude(objectMapper.writeValueAsString(requestBody));
    }

    public String calculateMealMacros(String mealDescription) throws Exception {
        Map<String, Object> tool = Map.of(
            "name", "calculate_meal_macros",
            "description", "Calculate macros for a meal description",
            "input_schema", Map.of(
                "type", "object",
                "required", List.of("protein", "calories", "carbs", "fat", "breakdown"),
                "properties", Map.of(
                    "protein",   Map.of("type", "number"),
                    "calories",  Map.of("type", "number"),
                    "carbs",     Map.of("type", "number"),
                    "fat",       Map.of("type", "number"),
                    "breakdown", Map.of("type", "array", "items", Map.of(
                        "type", "object",
                        "properties", Map.of(
                            "name",     Map.of("type", "string"),
                            "protein",  Map.of("type", "number"),
                            "calories", Map.of("type", "number"),
                            "carbs",    Map.of("type", "number"),
                            "fat",      Map.of("type", "number")
                        )
                    ))
                )
            )
        );

        Map<String, Object> requestBody = Map.of(
            "model", model,
            "max_tokens", 1000,
            "tools", List.of(tool),
            "tool_choice", Map.of("type", "tool", "name", "calculate_meal_macros"),
            "messages", List.of(
                Map.of("role", "user", "content", "Calculate macros for: " + mealDescription)
            )
        );

        String response = postToClaude(objectMapper.writeValueAsString(requestBody));

        // Parse tool use response — return just the input node as JSON string
        JsonNode root = objectMapper.readTree(response);
        JsonNode input = root.path("content").get(0).path("input");
        return input.toString();
    }
}