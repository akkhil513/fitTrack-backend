package com.fitTrack.resource;

import com.fitTrack.repository.UserRepository;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.Data;

@Path("/measurements")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MeasurementResource {

    @Inject
    UserRepository userRepository;

    @Data
    public static class MeasurementRequest {
        private String measurements;
    }

    @PUT
    @Path("/{userId}")
    public Response updateMeasurements(@PathParam("userId") String userId, MeasurementRequest body) {
        System.out.println("MeasurementResource called for userId: " + userId);
        System.out.println("Body: " + (body != null ? body.getMeasurements() : "null"));

        var existing = userRepository.getUser(userId);
        System.out.println("Existing user: " + (existing != null && !existing.isEmpty() ? "found" : "not found"));

        if (existing == null || existing.isEmpty()) {
            return Response.status(404).build();
        }
        userRepository.updateMeasurements(userId, body.getMeasurements() != null ? body.getMeasurements() : " ");
        return Response.ok("{\"message\": \"Measurements saved!\"}").build();
    }
}