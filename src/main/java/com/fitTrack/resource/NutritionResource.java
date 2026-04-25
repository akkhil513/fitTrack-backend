package com.fitTrack.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitTrack.service.ClaudeService;
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
    ClaudeService claudeService;

    private final ObjectMapper mapper = new ObjectMapper();

    @Data
    public static class MealRequest {
        private String description;
    }

    @POST
    @Path("/calculate")
    public Response calculateMacros(MealRequest request) {
        try {
            String claudeResponse = claudeService.calculateMealMacros(request.getDescription());
            JsonNode root = mapper.readTree(claudeResponse);
            String text = root.path("content").get(0).path("text").asText();
            text = text.replaceAll("```json\\n?", "").replaceAll("```\\n?", "").trim();
            JsonNode macros = mapper.readTree(text);
            return Response.ok(macros.toString()).build();
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage().replace("\"", "'") : e.getClass().getName();
            return Response.serverError()
                .entity("{\"error\": \"" + msg + "\"}")
                .build();
        }
    }
}

