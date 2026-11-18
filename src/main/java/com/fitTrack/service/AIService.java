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

        CHALLENGE DURATION RULES (read the user profile for duration):
        - 21 days: Single intense phase. Zero sugar focus. Maximum fat loss. Cardio after EVERY session. HIIT on pull days. High reps (12-15) for fat burn. Strategy must mention zero sugar protocol.
        - 45 days: Two phases — Phase 1 (days 1-21) fat loss, Phase 2 (days 22-45) lean bulk. Deload week 6.
        - 75 days: Three phases — fat loss, recomp, lean bulk. Deload weeks 6 and 12.
        - 100 days: Full periodization. Four phases. Deload weeks 4, 8, 12. Complete transformation.

        TRAINING RULES:
        - Always train back and biceps together on the same day. Never separate them.
        - Generate EXACTLY the number of training days the user specified. Never exceed it.
        - Follow Push/Pull/Legs split where possible.
        - For 21-day challenge: add incline walk 15 mins after every push session and HIIT 10 mins after every pull session.

        DAILY CHECKLIST RULES (strictly follow):
        - Generate 12-15 items minimum
        - Each supplement must be a SEPARATE item — never combine multiple supplements in one item
        - WRONG: 'Take Creatine + Whey Protein post workout'
        - RIGHT: item 1: 'Take 5g Creatine with water', item 2: 'Drink Whey Protein shake'
        - WRONG: 'Take Vitamin D3 and Fish Oil with breakfast'
        - RIGHT: item 1: 'Take Vitamin D3 2000 IU with breakfast', item 2: 'Take Fish Oil 2 caps with breakfast'
        - Each item must have ONE action only
        - Be specific with doses: '5g Creatine' not just 'Creatine'
        - Categories: supplement, training, recovery only (no nutrition)

        Required JSON structure:
        {
          "strategy": "string under 500 chars — must mention challenge duration and phase structure",
          "training": {
            "Monday": {"session": "string", "isRestDay": false, "exercises": [{"name": "string", "sets": 4, "reps": "8-10", "rest": "90s"}]},
            "Tuesday": {"session": "string", "isRestDay": false, "exercises": [...]},
            "Wednesday": {"session": "string", "isRestDay": false, "exercises": [...]},
            "Thursday": {"session": "string", "isRestDay": false, "exercises": [...]},
            "Friday": {"session": "string", "isRestDay": false, "exercises": [...]},
            "Saturday": {"session": "Active Recovery", "isRestDay": true, "exercises": []},
            "Sunday": {"session": "Rest Day", "isRestDay": true, "exercises": []}
          },
          "nutrition": "string under 500 chars — include calorie and protein targets based on duration goal",
          "supplements": "string under 500 chars",
          "recovery": "string under 500 chars",
          "dailyChecklist": [
            {"id": "s1", "label": "Take 5g Creatine monohydrate with water", "category": "supplement", "time": "Post-workout"},
            {"id": "s2", "label": "Drink Whey Protein shake (1 scoop)", "category": "supplement", "time": "Post-workout"},
            {"id": "s3", "label": "Take Vitamin D3 2000 IU", "category": "supplement", "time": "Morning"},
            {"id": "s4", "label": "Take Fish Oil 2 caps", "category": "supplement", "time": "With lunch"},
            {"id": "t1", "label": "Complete today training session", "category": "training", "time": "Morning"},
            {"id": "r1", "label": "Sleep 7-8 hours tonight", "category": "recovery", "time": "Before bed"}
          ]
        }

        CRITICAL FOR CHECKLIST:
        - One supplement per item — never combine
        - Include exact dose in every supplement item
        - Generate minimum 12 items total
        - Use the user's actual supplements from their profile

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

    /** Asks Gemini for one supplement time. Returns the raw model text; callers must validate it. */
    public String suggestSupplementTime(String prompt) throws Exception {
        return askShortAnswer(prompt);
    }

    /**
     * One low-temperature Gemini call for a short structured answer. Returns the raw model text;
     * callers must validate it. Throws on a non-200 status or an empty answer.
     */
    public String askShortAnswer(String prompt) throws Exception {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=" + getApiKey();
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "contents", new Object[]{
                        Map.of("parts", new Object[]{Map.of("text", prompt)})
                },
                "generationConfig", Map.of(
                        "temperature", 0.2,
                        "maxOutputTokens", 1000
                )
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Gemini API error: " + response.statusCode());
        }

        JsonNode text = objectMapper.readTree(response.body())
                .path("candidates").path(0).path("content").path("parts").path(0).path("text");
        if (!text.isTextual() || text.asText().isBlank()) {
            throw new IllegalStateException("Empty model response");
        }
        return text.asText();
    }

    public String generateWeeklyPlan(
            String userProfile,
            int weekNumber,
            String previousLogs) throws Exception {

        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=" + getApiKey();

        String weekContext = previousLogs.isEmpty()
                ? "This is week " + weekNumber + ". No previous data yet."
                : """
      This is week %d of the challenge.

      Here is everything the user actually logged last week:
      %s

      Use this data to decide:
      - Which muscle groups need more volume, and which need rest or a deload?
      - Should any exercises be swapped for variation or to fix weak points?
      - Do nutrition targets need adjusting? Compare logged calories and protein with the goal.

      WEIGHTS: the NEXT WEEK WORKING WEIGHTS listed above are already computed from the logs and are FIXED.
      For every exercise in that list, keep the exercise name exactly as written and set "targetWeight" to that number.
      For any exercise you add that is not in the list, set "targetWeight" to an empty string.
      If they logged fewer than 3 sessions, do not increase volume or intensity.
      """.formatted(weekNumber, previousLogs);
        String prompt = """
        You are an elite fitness coach generating week %d of a personalized fitness plan.

        CRITICAL: Respond with ONLY a valid JSON object. No markdown, no backticks.

        WEEK CONTEXT:
        %s

        PROGRESSIVE OVERLOAD RULES:
        - Increase weight or reps from last week based on logged performance
        - Never decrease intensity unless it is a deload week
        - Deload weeks: reduce volume by 40%%, keep movement patterns same

        Required JSON structure:
        {
          "strategy": "string — mention week number and what changed from last week",
          "training": {
            "Monday": {"session": "string", "isRestDay": false, "exercises": [{"name": "string", "sets": 4, "reps": "8-10", "rest": "90s", "targetWeight": "string"}]},
            "Tuesday": {"session": "string", "isRestDay": false, "exercises": [...]},
            "Wednesday": {"session": "string", "isRestDay": false, "exercises": [...]},
            "Thursday": {"session": "string", "isRestDay": false, "exercises": [...]},
            "Friday": {"session": "string", "isRestDay": false, "exercises": [...]},
            "Saturday": {"session": "Active Recovery", "isRestDay": true, "exercises": []},
            "Sunday": {"session": "Rest Day", "isRestDay": true, "exercises": []}
          },
          "nutrition": "string — update targets if needed based on week progress",
          "supplements": "string",
          "recovery": "string",
          "dailyChecklist": [
            {"id": "s1", "label": "string", "category": "supplement", "time": "Morning"},
            {"id": "t1", "label": "string", "category": "training", "time": "Evening"},
            {"id": "r1", "label": "string", "category": "recovery", "time": "Before bed"}
          ]
        }

        User Profile:
        %s
        """.formatted(weekNumber, weekContext, userProfile);

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

        if (response.statusCode() != 200) {
            throw new RuntimeException("Gemini error: " + response.statusCode());
        }

        return response.body();
    }
}