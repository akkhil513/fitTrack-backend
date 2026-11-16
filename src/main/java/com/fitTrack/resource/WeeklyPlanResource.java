package com.fitTrack.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitTrack.repository.PlanRepository;
import com.fitTrack.service.AIService;
import com.fitTrack.service.WeeklyPlanService;
import com.fitTrack.security.CallerIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.HashMap;
import java.util.Map;

@Path("/plans")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WeeklyPlanResource {

    @Inject AIService aiService;
    @Inject PlanRepository planRepository;
    @Inject ObjectMapper objectMapper;
    @Inject WeeklyPlanService weeklyPlanService;

    @POST
    @Path("/generate-weekly")
    public Response generateWeekly(@HeaderParam("Authorization") String authorization, Map<String, Object> body) {
        CallerIdentity.requireSelf(authorization, (String) body.get("userId"));
        new Thread(() -> {
            try {
                String userId = (String) body.get("userId");
                int weekNumber = Integer.parseInt(body.get("weekNumber").toString());
                String previousLogs = body.getOrDefault("previousLogs", "").toString();
                String userProfile = body.getOrDefault("userProfile", "").toString();

                // Save GENERATING status
                Map<String, AttributeValue> generatingItem = new HashMap<>();
                generatingItem.put("userId", AttributeValue.fromS(userId));
                generatingItem.put("status", AttributeValue.fromS("GENERATING"));
                generatingItem.put("weekNumber", AttributeValue.fromN(String.valueOf(weekNumber)));
                planRepository.savePlan(generatingItem);

                // Call Gemini
                String geminiResponse = aiService.generateWeeklyPlan(
                        userProfile, weekNumber, previousLogs);

                // Parse response
                JsonNode root = objectMapper.readTree(geminiResponse);
                String text = root.path("candidates").get(0)
                        .path("content").path("parts").get(0)
                        .path("text").asText();
                text = text.replaceAll("```json\\n?", "")
                        .replaceAll("```\\n?", "").trim();

                JsonNode planNode = objectMapper.readTree(text);

                // Save READY plan
                Map<String, AttributeValue> readyItem = new HashMap<>();
                readyItem.put("userId", AttributeValue.fromS(userId));
                readyItem.put("status", AttributeValue.fromS("READY"));
                readyItem.put("weekNumber", AttributeValue.fromN(String.valueOf(weekNumber)));
                readyItem.put("strategy", AttributeValue.fromS(
                        planNode.path("strategy").asText("")));
                readyItem.put("training", AttributeValue.fromS(
                        planNode.path("training").toString()));
                readyItem.put("nutrition", AttributeValue.fromS(
                        planNode.path("nutrition").asText("")));
                readyItem.put("supplements", AttributeValue.fromS(
                        planNode.path("supplements").asText("")));
                readyItem.put("recovery", AttributeValue.fromS(
                        planNode.path("recovery").asText("")));
                readyItem.put("dailyChecklist", AttributeValue.fromS(
                        planNode.path("dailyChecklist").toString()));
                planRepository.savePlan(readyItem);

                System.out.println("Weekly plan week " + weekNumber +
                        " saved for " + userId);

            } catch (Exception e) {
                System.out.println("Weekly plan error: " + e.getMessage());
            }
        }).start();

        return Response.ok("{\"status\":\"GENERATING\"}").build();
    }

    @POST
    @Path("/generate-weekly-scheduled")
    public Response generateWeeklyScheduled(@HeaderParam("Authorization") String authorization, Map<String, Object> body) {
        CallerIdentity.requireNoCaller(authorization);
        String source = body.getOrDefault("source", "").toString();
        if (!"eventbridge".equals(source)) {
            return Response.status(403).build();
        }

        try {
            weeklyPlanService.generateAllUsersWeeklyPlans();
        } catch (Exception e) {
            System.out.println("Weekly scheduled error: " + e.getMessage());
        }

        return Response.ok("{\"status\":\"done\"}").build();
    }
}