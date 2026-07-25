package com.fitTrack.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@ApplicationScoped
public class AIService {

    private static volatile String cachedApiKey;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final SecretsManagerClient secretsClient = SecretsManagerClient.builder()
            .region(software.amazon.awssdk.regions.Region.US_EAST_1)
            .build();

    private String getApiKey() {
        if (cachedApiKey != null) return cachedApiKey;
        synchronized (AIService.class) {
            if (cachedApiKey != null) return cachedApiKey;
            try {
                var response = secretsClient.getSecretValue(
                        GetSecretValueRequest.builder()
                                .secretId("fittrack/gemini-api-key")
                                .build()
                );
                cachedApiKey = objectMapper.readTree(response.secretString())
                        .path("GEMINI_API_KEY").asText();
                return cachedApiKey;
            } catch (Exception e) {
                throw new RuntimeException("Failed to get Gemini API key: " + e.getMessage());
            }
        }
    }

    public String generatePlan(String userProfile) throws Exception {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=" + getApiKey();

        String prompt = """
            You are an elite fitness coach. Generate a personalized fitness plan for this user.

            CRITICAL: Respond with ONLY a valid JSON object. No markdown, no backticks, no explanation.

            Required JSON structure:
            {
              "strategy": "string under 500 chars",
              "training": {
                "Monday": {"session": "string", "isRestDay": false, "exercises": [{"name": "string", "sets": 4, "reps": "8-10", "rest": "90s"}]},
                "Tuesday": {"session": "string", "isRestDay": false, "exercises": [...]},
                "Wednesday": {"session": "string", "isRestDay": false, "exercises": [...]},
                "Thursday": {"session": "string", "isRestDay": false, "exercises": [...]},
                "Friday": {"session": "string", "isRestDay": false, "exercises": [...]},
                "Saturday": {"session": "Active Recovery", "isRestDay": true, "exercises": []},
                "Sunday": {"session": "Rest Day", "isRestDay": true, "exercises": []}
              },
              "nutrition": "string under 500 chars",
              "supplements": "string under 500 chars",
              "recovery": "string under 500 chars",
              "dailyChecklist": [
                {"id": "s1", "label": "string", "category": "supplement", "time": "Morning"},
                {"id": "t1", "label": "string", "category": "training", "time": "Evening"},
                {"id": "r1", "label": "string", "category": "recovery", "time": "Before bed"}
              ]
            }

            User Profile:
            """ + userProfile;

        String requestBody = objectMapper.writeValueAsString(Map.of(
                "contents", new Object[]{
                        Map.of("parts", new Object[]{
                                Map.of("text", prompt)
                        })
                },
                "generationConfig", Map.of(
                        "temperature", 0.7,
                        "maxOutputTokens", 8192
                )
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        System.out.println("Gemini status: " + response.statusCode());
        System.out.println("Gemini response: " + response.body());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Gemini API error: " + response.statusCode() + " " + response.body());
        }

        return response.body();
    }

    public String calculateMealMacros(String mealDescription) throws Exception {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=" + getApiKey();
        String prompt = """
            You are a nutrition expert. Calculate macros for this meal.
            Respond with ONLY a JSON object, no markdown:
            {"protein": number, "calories": number, "carbs": number, "fat": number,
             "breakdown": [{"name": "string", "protein": number, "calories": number, "carbs": number, "fat": number}]}

            Meal: """ + mealDescription;

        String requestBody = objectMapper.writeValueAsString(Map.of(
                "contents", new Object[]{
                        Map.of("parts", new Object[]{
                                Map.of("text", prompt)
                        })
                },
                "generationConfig", Map.of(
                        "temperature", 0.3,
                        "maxOutputTokens", 1000
                )
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Gemini API error: " + response.statusCode());
        }

        // Parse Gemini response
        JsonNode root = objectMapper.readTree(response.body());
        String text = root.path("candidates").get(0)
                .path("content").path("parts").get(0)
                .path("text").asText();

        // Clean JSON
        text = text.replaceAll("```json\\n?", "")
                .replaceAll("```\\n?", "")
                .trim();

        return text;
    }
}