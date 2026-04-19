package com.fitTrack.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

@ApplicationScoped
public class ClaudeService {

    @ConfigProperty(name = "fittrack.claude.api-key")
    String apiKey;

    @ConfigProperty(name = "fittrack.claude.model")
    String model;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    public String generatePlan(String userProfile) throws Exception {

        String jsonBody = getRequestString(userProfile);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.anthropic.com/v1/messages"))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
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
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_tokens", 2048,
                "system", "You are an elite fitness coach with 15+ years experience specializing in body recomposition, hypertrophy, and sports nutrition. You must respond with ONLY a JSON object. No markdown, no backticks, no explanation, no extra text before or after. The JSON must have exactly these 5 keys: strategy, training, nutrition, supplements, recovery. Each value must be a single detailed string under 300 characters. Base your response on the user profile provided.",
                "messages", new Object[]{
                        Map.of("role", "user", "content", userProfile)
                }
        );

        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(requestBody);
    }
}