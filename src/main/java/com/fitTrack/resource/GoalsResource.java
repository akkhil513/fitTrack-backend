package com.fitTrack.resource;

import com.fitTrack.repository.UserRepository;
import com.fitTrack.security.CallerIdentity;
import com.fitTrack.service.Goals;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.Data;

/** Stores the caller's goal targets. They are returned with the user record (GET /users/{id}). */
@Path("/goals")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GoalsResource {

    @Inject
    UserRepository userRepository;

    @Data
    public static class GoalsRequest {
        private String goals;
    }

    @PUT
    @Path("/{userId}")
    public Response saveGoals(@HeaderParam("Authorization") String authorization,
                              @PathParam("userId") String userId,
                              GoalsRequest body) {
        CallerIdentity.requireSelf(authorization, userId);

        String normalized;
        try {
            normalized = Goals.normalize(body == null ? null : body.getGoals());
        } catch (IllegalArgumentException e) {
            // Messages are fixed strings from Goals, never echoed user input.
            return Response.status(400).entity("{\"message\": \"" + e.getMessage() + "\"}").build();
        }

        var existing = userRepository.getUser(userId);
        if (existing == null || existing.isEmpty()) {
            return Response.status(404).build();
        }
        userRepository.updateGoals(userId, normalized);
        return Response.ok("{\"goals\": " + normalized + "}").build();
    }
}
