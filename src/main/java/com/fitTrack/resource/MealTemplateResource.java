package com.fitTrack.resource;

import com.fitTrack.repository.UserRepository;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.Data;

@Path("/meal-templates")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MealTemplateResource {

    @Inject
    UserRepository userRepository;

    @Data
    public static class MealTemplateRequest {
        private String mealTemplates;
    }

    @PUT
    @Path("/{userId}")
    public Response updateMealTemplates(@PathParam("userId") String userId, MealTemplateRequest body) {
        var existing = userRepository.getUser(userId);
        if (existing == null || existing.isEmpty()) {
            return Response.status(404).build();
        }
        userRepository.updateMealTemplates(userId, body.getMealTemplates());
        return Response.ok("{\"message\": \"Meal templates saved!\"}").build();
    }

    @GET
    @Path("/{userId}")
    public Response getMealTemplates(@PathParam("userId") String userId) {
        var item = userRepository.getUser(userId);
        if (item == null || item.isEmpty()) {
            return Response.status(404).build();
        }
        String templates = item.getOrDefault("mealTemplates",
                software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromS(" ")).s();
        return Response.ok("{\"mealTemplates\": " +
                (templates.trim().isEmpty() || templates.trim().equals(" ") ? "[]" : templates) + "}").build();
    }
}

