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
        private String measurementHistory;
    }

    @PUT
    @Path("/{userId}")
    public Response updateMeasurements(@PathParam("userId") String userId, MeasurementRequest body) {
        var existing = userRepository.getUser(userId);

        if (existing == null || existing.isEmpty()) {
            return Response.status(404).build();
        }
        userRepository.updateMeasurements(userId,
            body.getMeasurements() != null ? body.getMeasurements() : " ",
            body.getMeasurementHistory() != null ? body.getMeasurementHistory() : " "
        );
        return Response.ok("{\"message\": \"Measurements saved!\"}").build();
    }
}