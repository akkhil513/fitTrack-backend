package com.fitTrack.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitTrack.mapper.PlanMapper;
import com.fitTrack.model.FitPlan;
import com.fitTrack.model.OnboardingRequest;
import com.fitTrack.repository.PlanRepository;
import com.fitTrack.service.AIService;
import com.fitTrack.security.CallerIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.fitTrack.service.ExerciseSwap;
import com.fitTrack.service.SlidingWindowLimiter;
import lombok.Data;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Path("/plans")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PlanResource {

    @Inject
    PlanRepository planRepository;

    @Inject
    AIService AIService;

    @Inject
    com.fitTrack.repository.UserRepository userRepository;

    private static final Logger LOG = Logger.getLogger(PlanResource.class);

    /** Each swap costs a Gemini call: cap them per user (best effort, per warm container). */
    private static final SlidingWindowLimiter SWAP_LIMIT = new SlidingWindowLimiter(20, Duration.ofHours(1));

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Data
    public static class SwapRequest {
        private String day;
        private String exercise;
        private String reason;
    }

    @POST
    @Path("/generate")
    public Response generatePlan(@HeaderParam("Authorization") String authorization, OnboardingRequest request) {
        CallerIdentity.requireSelf(authorization, request.getUserId());
        try {
            System.out.println("Generating plan: " + request.getUserId());

            // Delete old plan if exists
            planRepository.deletePlan(request.getUserId());

            String userProfile = buildUserProfile(request);
            String claudeResponse = AIService.generatePlan(userProfile);

            JsonNode root = objectMapper.readTree(claudeResponse);

            // Gemini response structure
            String text = root.path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText();

            System.out.println("Gemini text: " + text);

            // Clean JSON markers if present
            text = text.replaceAll("```json\\n?", "")
                    .replaceAll("```\\n?", "")
                    .trim();

            JsonNode planNode = objectMapper.readTree(text);

            FitPlan plan = new FitPlan();
            plan.setUserId(request.getUserId());
            plan.setPlanId(UUID.randomUUID().toString());
            plan.setCreatedAt(Instant.now().toString());
            plan.setStrategy(planNode.path("strategy").asText());
            plan.setTraining(planNode.path("training").toString());
            plan.setNutrition(planNode.path("nutrition").asText());
            plan.setSupplements(planNode.path("supplements").asText());
            plan.setRecovery(planNode.path("recovery").asText());
            plan.setStatus("READY");
            // A freshly generated plan is week 1. Without this the field defaults to 0
            // and the Monday weekly job treats the plan as stale and overwrites it.
            plan.setWeekNumber(1);
            plan.setProfile(userProfile);
            plan.setDailyChecklist(planNode.path("dailyChecklist").toString());

            planRepository.savePlan(PlanMapper.toMap(plan));

            // The plan was written for this length, so make the user record agree with it
            // even if the client's separate profile update failed.
            Integer days = parseDuration(request.getChallengeDuration());
            if (days != null) {
                userRepository.setChallengeDuration(request.getUserId(), String.valueOf(days));
            }
            return Response.ok("{\"status\": \"READY\"}").build();

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
            return Response.serverError()
                    .entity("{\"message\": \"" + e.getMessage() + "\"}")
                    .build();
        }
    }

    /** A whole number of days between 7 and 365, or null when the value is missing or unusable. */
    static Integer parseDuration(String raw) {
        try {
            int days = Integer.parseInt(raw == null ? "" : raw.trim());
            return days >= 7 && days <= 365 ? days : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String buildUserProfile(OnboardingRequest request) {
        return """
        Age: %s, Gender: %s, Height: %s, Weight: %s kg,
        Physique: %s, Fat storage: %s, Goal: %s,
        CHALLENGE DURATION: %s DAYS (generate plan scoped to exactly this duration),
        Training level: %s, Gym: %s,
        Training days per week: %s, Session duration: %s, Preferred time: %s,
        Diet: %s, Food preference: %s, Appetite: %s,
        Current supplements: %s, Injuries: %s,
        Sleep: %s, Stress: %s, Coaching style: %s,
        Bulk approach: %s, Visual goals: %s,
        Uses protein powder: %s, Protein powder type: %s,
        Needs restock: %s, Wants protein recommendation: %s
        """.formatted(
                request.getAge(), request.getGender(), request.getHeight(),
                request.getWeight(), request.getPhysique(), request.getFatStorage(),
                request.getPrimaryGoal(),
                request.getChallengeDuration() != null ? request.getChallengeDuration() : "100",
                request.getTrainingLevel(), request.getGymAccess(),
                request.getDaysPerWeek(), request.getSessionDuration(),
                request.getPreferredTrainTime(), request.getDietType(),
                request.getFoodPreference(), request.getAppetite(),
                request.getSupplements(), request.getInjuries(),
                request.getSleepHours(), request.getStressLevel(),
                request.getCoachingStyle(), request.getBulkApproach(),
                request.getVisualGoals(), request.isUsesProteinPowder(),
                request.getProteinPowderType(), request.isNeedsProteinRestock(),
                request.isWantsProteinRecommendation()
        );
    }

    @POST
    @Path("/createPlan")
    public Response savePlan(@HeaderParam("Authorization") String authorization, FitPlan plan) {
        CallerIdentity.requireSelf(authorization, plan.getUserId());
        plan.setPlanId(UUID.randomUUID().toString());
        plan.setCreatedAt(Instant.now().toString());
        planRepository.savePlan(PlanMapper.toMap(plan));
        return Response.ok("{\"message\": \"Plan saved successfully!\"}").build();
    }

    @GET
    @Path("/{userId}")
    public Response getPlan(@HeaderParam("Authorization") String authorization, @PathParam("userId") String userId) {
        CallerIdentity.requireSelf(authorization, userId);
        var item = planRepository.getPlan(userId);
        if (item == null || item.isEmpty()) {
            return Response.status(404).build();
        }
        return Response.ok(PlanMapper.toPlan(item)).build();
    }

    // POST /plans/{userId}/swap: replace one exercise with an AI-suggested alternative.
    @POST
    @Path("/{userId}/swap")
    public Response swapExercise(@HeaderParam("Authorization") String authorization,
                                 @PathParam("userId") String userId,
                                 SwapRequest body) {
        CallerIdentity.requireSelf(authorization, userId);
        if (body == null) {
            return badRequest("Missing request body");
        }

        String day;
        String reason;
        try {
            day = ExerciseSwap.validDay(body.getDay());
            reason = ExerciseSwap.cleanReason(body.getReason());
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
        if (body.getExercise() == null || body.getExercise().isBlank() || body.getExercise().length() > 100) {
            return badRequest("Choose an exercise to replace");
        }

        var item = planRepository.getPlan(userId);
        if (item == null || item.isEmpty()) {
            return Response.status(404).build();
        }
        FitPlan plan = PlanMapper.toPlan(item);
        if (!"READY".equals(plan.getStatus())) {
            return badRequest("Your plan is not ready yet");
        }

        com.fasterxml.jackson.databind.JsonNode training;
        try {
            training = objectMapper.readTree(plan.getTraining());
        } catch (Exception e) {
            return Response.status(409).entity(Map.of("message", "Your plan could not be read")).build();
        }
        var dayNode = training.path(day);
        var current = ExerciseSwap.find(dayNode, body.getExercise());
        if (current == null || dayNode.path("isRestDay").asBoolean(false)) {
            return badRequest("That exercise is not in your plan");
        }

        if (!SWAP_LIMIT.tryAcquire(userId, System.currentTimeMillis())) {
            return Response.status(429).entity(Map.of("message", "Too many swaps. Try again later.")).build();
        }

        try {
            var names = ExerciseSwap.namesIn(dayNode);
            String prompt = ExerciseSwap.buildPrompt(
                    plan.getProfile(), dayNode.path("session").asText(""), current, names, reason);
            var replacement = ExerciseSwap.parse(
                    AIService.askShortAnswer(prompt), names, current.path("name").asText(""));
            String updated = ExerciseSwap.apply(
                    plan.getTraining(), day, current.path("name").asText(""), replacement);
            planRepository.updateTraining(userId, updated);
            return Response.ok(Map.of("training", updated)).build();
        } catch (Exception e) {
            // Never echo model output or exception text to the caller.
            LOG.errorf("Exercise swap failed: %s", e.getClass().getSimpleName());
            return Response.status(502).entity(Map.of("message", "Could not suggest a swap. Try again.")).build();
        }
    }

    private static Response badRequest(String message) {
        return Response.status(400).entity(Map.of("message", message)).build();
    }
}
