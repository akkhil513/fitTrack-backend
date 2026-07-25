package com.fitTrack.resource;

import com.fitTrack.service.AIService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.Data;

@Path("/nutrition")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class NutritionResource {

    @Inject
    AIService AIService;

    @Data
    public static class MealRequest {
        private String description;
    }

    @POST
    @Path("/calculate")
    public Response calculateMacros(MealRequest request) {
        try {
            String macros = AIService.calculateMealMacros(request.getDescription());
            return Response.ok(macros).build();
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage().replace("\"", "'") : e.getClass().getName();
            return Response.serverError()
                .entity("{\"error\": \"" + msg + "\"}")
                .build();
        }
    }
}

