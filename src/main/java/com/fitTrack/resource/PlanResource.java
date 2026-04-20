package com.fitTrack.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitTrack.mapper.PlanMapper;
import com.fitTrack.model.FitPlan;
import com.fitTrack.model.OnboardingRequest;
import com.fitTrack.repository.PlanRepository;
import com.fitTrack.service.ClaudeService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.util.UUID;

@Path("/plans")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PlanResource {

    @Inject
    PlanRepository planRepository;

    @Inject
    ClaudeService claudeService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @POST
    @Path("/generate")
    public Response generatePlan(OnboardingRequest request) {
        try {
            // Build user profile string to send to Claude
            String userProfile = """
                    Age: %s, Gender: %s, Height: %s, Weight: %s,
                    Physique: %s, Fat storage: %s, Goal: %s,
                    Training level: %s, Gym: %s, Days/week: %s,
                    Session duration: %s, Diet: %s, Food preference: %s,
                    Appetite: %s, Supplements: %s, Injuries: %s,
                    Sleep: %s, Stress: %s, Coaching style: %s,
                    Bulk approach: %s, Visual goals: %s
                    """.formatted(
                    request.getAge(), request.getGender(), request.getHeight(),
                    request.getWeight(), request.getPhysique(), request.getFatStorage(),
                    request.getPrimaryGoal(), request.getTrainingLevel(), request.getGymAccess(),
                    request.getDaysPerWeek(), request.getSessionDuration(), request.getDietType(),
                    request.getFoodPreference(), request.getAppetite(), request.getSupplements(),
                    request.getInjuries(), request.getSleepHours(), request.getStressLevel(),
                    request.getCoachingStyle(), request.getBulkApproach(), request.getVisualGoals()
            );

            String claudeResponse = claudeService.generatePlan(userProfile);

            JsonNode root = objectMapper.readTree(claudeResponse);
            String planJson = root.path("content").get(0).path("text").asText();

            planJson = planJson.trim();
            planJson = planJson.replaceAll("```json\\n?", "")
                    .replaceAll("```\\n?", "")
                    .trim();

            int start = planJson.indexOf("{");
            int end = planJson.lastIndexOf("}");
            if (start != -1 && end != -1) {
                planJson = planJson.substring(start, end + 1);
            }

            JsonNode planNode = objectMapper.readTree(planJson);

            // Build and save plan
            FitPlan plan = new FitPlan();
            plan.setUserId(request.getUserId());
            plan.setPlanId(UUID.randomUUID().toString());
            plan.setCreatedAt(Instant.now().toString());
            plan.setStrategy(planNode.path("strategy").toString());
            plan.setTraining(planNode.path("training").toString());
            plan.setNutrition(planNode.path("nutrition").toString());
            plan.setSupplements(planNode.path("supplements").toString());
            plan.setRecovery(planNode.path("recovery").toString());

            planRepository.savePlan(PlanMapper.toMap(plan));

            return Response.ok(plan).build();

        } catch (Exception e) {
            return Response.serverError()
                    .entity("{\"message\": \"" + e.getMessage() + "\"}")
                    .build();
        }
    }

    @POST
    @Path("/createPlan")
    public Response savePlan(FitPlan plan) {
        plan.setPlanId(UUID.randomUUID().toString());
        plan.setCreatedAt(Instant.now().toString());
        planRepository.savePlan(PlanMapper.toMap(plan));
        return Response.ok("{\"message\": \"Plan saved successfully!\"}").build();
    }

    @GET
    @Path("/{userId}")
    public Response getPlan(@PathParam("userId") String userId) {
        var item = planRepository.getPlan(userId);
        if (item == null || item.isEmpty()) {
            return Response.status(404).build();
        }
        return Response.ok(PlanMapper.toPlan(item)).build();
    }
}