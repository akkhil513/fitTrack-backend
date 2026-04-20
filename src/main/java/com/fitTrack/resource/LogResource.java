package com.fitTrack.resource;

import com.fitTrack.mapper.LogMapper;
import com.fitTrack.model.FitLog;
import com.fitTrack.repository.LogRepository;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;
import java.util.stream.Collectors;

@Path("/logs")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class LogResource {

    @Inject
    LogRepository logRepository;

    @POST
    @Path("/createLog")
    public Response saveLog(FitLog log) {
        if (log.getWorkout() != null) {
            log.setSession(log.getWorkout().getOrDefault("session", ""));
            log.setExercises(log.getWorkout().getOrDefault("exercises", ""));
            log.setNotes(log.getWorkout().getOrDefault("notes", ""));
        }
        if (log.getNutrition() != null) {
            log.setProtein(log.getNutrition().getOrDefault("protein", ""));
            log.setCalories(log.getNutrition().getOrDefault("calories", ""));
            log.setWater(log.getNutrition().getOrDefault("water", ""));
            log.setSleep(log.getNutrition().getOrDefault("sleep", ""));
        }

        // Check if log exists for this date
        var existing = logRepository.getLog(log.getUserId(), log.getDate());

        if (existing != null && !existing.isEmpty()) {
            // Merge with existing — don't overwrite non-empty fields
            String session = log.getSession() != null && !log.getSession().isBlank()
                    ? log.getSession()
                    : existing.getOrDefault("session", AttributeValue.fromS(" ")).s();
            String exercises = log.getExercises() != null && !log.getExercises().isBlank()
                    ? log.getExercises()
                    : existing.getOrDefault("exercises", AttributeValue.fromS(" ")).s();
            String protein = log.getProtein() != null && !log.getProtein().isBlank()
                    ? log.getProtein()
                    : existing.getOrDefault("protein", AttributeValue.fromS(" ")).s();
            String calories = log.getCalories() != null && !log.getCalories().isBlank()
                    ? log.getCalories()
                    : existing.getOrDefault("calories", AttributeValue.fromS(" ")).s();
            String water = log.getWater() != null && !log.getWater().isBlank()
                    ? log.getWater()
                    : existing.getOrDefault("water", AttributeValue.fromS(" ")).s();
            String sleep = log.getSleep() != null && !log.getSleep().isBlank()
                    ? log.getSleep()
                    : existing.getOrDefault("sleep", AttributeValue.fromS(" ")).s();
            String notes = log.getNotes() != null && !log.getNotes().isBlank()
                    ? log.getNotes()
                    : existing.getOrDefault("notes", AttributeValue.fromS(" ")).s();

            logRepository.updateLog(log.getUserId(), log.getDate(), Map.of(
                    ":s",  AttributeValue.fromS(session),
                    ":e",  AttributeValue.fromS(exercises),
                    ":p",  AttributeValue.fromS(protein),
                    ":c",  AttributeValue.fromS(calories),
                    ":w",  AttributeValue.fromS(water),
                    ":sl", AttributeValue.fromS(sleep),
                    ":n",  AttributeValue.fromS(notes)
            ));
        } else {
            logRepository.saveLog(LogMapper.toMap(log));
        }

        return Response.ok("{\"message\": \"Log saved successfully!\"}").build();
    }
    // GET /logs/{userId}/{date} — gets log for specific date
    @GET
    @Path("/{userId}/{date}")
    public Response getLog(@PathParam("userId") String userId, @PathParam("date") String date) {
        var item = logRepository.getLog(userId, date);
        if (item == null || item.isEmpty()) {
            return Response.status(404).build();
        }
        return Response.ok(LogMapper.toLog(item)).build();
    }

    // GET /logs/{userId} — gets all logs for a user
    @GET
    @Path("/{userId}")
    public Response getLogs(@PathParam("userId") String userId) {
        var items = logRepository.getLogs(userId);
        var logs = items.stream().map(LogMapper::toLog).collect(Collectors.toList());
        return Response.ok(logs).build();
    }

    @PUT
    @Path("/update/{userId}/{date}")
    public Response updateLog(@PathParam("userId") String userId,
                              @PathParam("date") String date,
                              FitLog log) {
        var existing = logRepository.getLog(userId, date);
        if (existing == null || existing.isEmpty()) {
            return Response.status(404)
                    .entity("{\"message\": \"Log not found\"}")
                    .build();
        }
        log.setUserId(userId);
        log.setDate(date);

        // Extract workout fields if nested
        if (log.getWorkout() != null) {
            log.setSession(log.getWorkout().getOrDefault("session", ""));
            log.setExercises(log.getWorkout().getOrDefault("exercises", ""));
            log.setNotes(log.getWorkout().getOrDefault("notes", ""));
        }
        if (log.getNutrition() != null) {
            log.setProtein(log.getNutrition().getOrDefault("protein", ""));
            log.setCalories(log.getNutrition().getOrDefault("calories", ""));
            log.setWater(log.getNutrition().getOrDefault("water", ""));
            log.setSleep(log.getNutrition().getOrDefault("sleep", ""));
        }

        // Merge with existing — don't overwrite non-empty fields
        String session = isValid(log.getSession()) ? log.getSession() : existing.getOrDefault("session", AttributeValue.fromS(" ")).s();
        String exercises = isValid(log.getExercises()) ? log.getExercises() : existing.getOrDefault("exercises", AttributeValue.fromS(" ")).s();
        String protein = isValid(log.getProtein()) ? log.getProtein() : existing.getOrDefault("protein", AttributeValue.fromS(" ")).s();
        String calories = isValid(log.getCalories()) ? log.getCalories() : existing.getOrDefault("calories", AttributeValue.fromS(" ")).s();
        String water = isValid(log.getWater()) ? log.getWater() : existing.getOrDefault("water", AttributeValue.fromS(" ")).s();
        String sleep = isValid(log.getSleep()) ? log.getSleep() : existing.getOrDefault("sleep", AttributeValue.fromS(" ")).s();
        String notes = isValid(log.getNotes()) ? log.getNotes() : existing.getOrDefault("notes", AttributeValue.fromS(" ")).s();

        logRepository.updateLog(userId, date, Map.of(
                ":s",  AttributeValue.fromS(session),
                ":e",  AttributeValue.fromS(exercises),
                ":p",  AttributeValue.fromS(protein),
                ":c",  AttributeValue.fromS(calories),
                ":w",  AttributeValue.fromS(water),
                ":sl", AttributeValue.fromS(sleep),
                ":n",  AttributeValue.fromS(notes)
        ));

        return Response.ok("{\"message\": \"Log updated successfully!\"}").build();
    }

    private boolean isValid(String value) {
        return value != null && !value.isBlank();
    }
}