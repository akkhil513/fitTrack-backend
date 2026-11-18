package com.fitTrack.resource;

import com.fitTrack.repository.UserRepository;
import com.fitTrack.security.CallerIdentity;
import com.fitTrack.service.AIService;
import com.fitTrack.service.SlidingWindowLimiter;
import com.fitTrack.service.SupplementReminders;
import com.fitTrack.service.SupplementTimeSuggestion;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.Data;
import org.jboss.logging.Logger;

import java.time.Duration;

/** Stores the caller's supplement reminders. They are returned with the user record (GET /users/{id}). */
@Path("/supplements")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SupplementResource {

    private static final Logger LOG = Logger.getLogger(SupplementResource.class);

    /** Each Gemini call costs money: cap suggestions per user (best effort, per warm container). */
    private static final SlidingWindowLimiter SUGGESTION_LIMIT =
            new SlidingWindowLimiter(20, Duration.ofHours(1));

    @Inject
    UserRepository userRepository;

    @Inject
    AIService aiService;

    @Data
    public static class SuggestTimeRequest {
        private String name;
    }

    @Data
    public static class SupplementRequest {
        private String supplementReminders;
    }

    @PUT
    @Path("/{userId}")
    public Response saveReminders(@HeaderParam("Authorization") String authorization,
                                  @PathParam("userId") String userId,
                                  SupplementRequest body) {
        CallerIdentity.requireSelf(authorization, userId);

        String normalized;
        try {
            normalized = SupplementReminders.normalize(body == null ? null : body.getSupplementReminders());
        } catch (IllegalArgumentException e) {
            // Messages are fixed strings from SupplementReminders, never echoed user input.
            return Response.status(400).entity("{\"message\": \"" + e.getMessage() + "\"}").build();
        }

        var existing = userRepository.getUser(userId);
        if (existing == null || existing.isEmpty()) {
            return Response.status(404).build();
        }
        userRepository.updateSupplementReminders(userId, normalized);
        return Response.ok("{\"supplementReminders\": " + normalized + "}").build();
    }

    @POST
    @Path("/{userId}/suggest-time")
    public Response suggestTime(@HeaderParam("Authorization") String authorization,
                                @PathParam("userId") String userId,
                                SuggestTimeRequest body) {
        CallerIdentity.requireSelf(authorization, userId);

        String prompt;
        try {
            prompt = SupplementTimeSuggestion.buildPrompt(
                    SupplementTimeSuggestion.cleanName(body == null ? null : body.getName()));
        } catch (IllegalArgumentException e) {
            return Response.status(400).entity("{\"message\": \"" + e.getMessage() + "\"}").build();
        }

        if (!SUGGESTION_LIMIT.tryAcquire(userId, System.currentTimeMillis())) {
            return Response.status(429)
                    .entity("{\"message\": \"Too many suggestions. Try again later.\"}")
                    .build();
        }

        try {
            var suggestion = SupplementTimeSuggestion.parse(aiService.suggestSupplementTime(prompt));
            return Response.ok(SupplementTimeSuggestion.toJson(suggestion)).build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return unavailable(e);
        } catch (Exception e) {
            return unavailable(e);
        }
    }

    /** Logs the failure type only: Gemini errors can carry request details, never the user's text. */
    private static Response unavailable(Exception e) {
        LOG.warnf("Supplement time suggestion failed: %s", e.getClass().getSimpleName());
        return Response.status(502)
                .entity("{\"message\": \"Suggestion unavailable right now\"}")
                .build();
    }
}
