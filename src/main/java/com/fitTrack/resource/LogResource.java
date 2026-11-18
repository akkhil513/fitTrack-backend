package com.fitTrack.resource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitTrack.mapper.LogMapper;
import com.fitTrack.model.FitLog;
import com.fitTrack.repository.LogRepository;
import com.fitTrack.security.CallerIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.Data;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Path("/logs")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class LogResource {

    /** How far back a missed day can still be marked as skipped. */
    static final int SKIP_WINDOW_DAYS = 14;

    /** Caps so one log cannot grow without bound: about 25 exercises with sets fit well inside this. */
    static final int MAX_EXERCISES_CHARS = 30_000;
    static final int MAX_SESSION_CHARS = 80;

    private static final Set<String> WEEKDAYS =
            Set.of("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday");

    @Inject
    LogRepository logRepository;

    @ConfigProperty(name = "app.zone", defaultValue = "America/New_York")
    String zoneId;

    @Data
    public static class SkipRequest {
        private Boolean skipped;
        private int dayNumber;
    }

    @POST
    @Path("/createLog")
    public Response saveLog(@HeaderParam("Authorization") String authorization, FitLog log) {
        CallerIdentity.requireSelf(authorization, log.getUserId());
        if (!isSkippable(log.getDate(), LocalDate.now(ZoneId.of(zoneId)))) {
            return Response.status(400)
                    .entity("{\"message\": \"Logs can be saved for the last " + SKIP_WINDOW_DAYS + " days, not the future\"}")
                    .build();
        }
        String planned = normalizePlannedDay(log.getPlannedDay());
        String rest = normalizeRest(log.getRest());
        if (planned == null || rest == null) {
            return Response.status(400).entity("{\"message\": \"Invalid plannedDay or rest\"}").build();
        }
        log.setPlannedDay(planned);
        log.setRest(rest);
        extractNestedFields(log);
        if (tooLarge(log.getExercises(), MAX_EXERCISES_CHARS) || tooLarge(log.getSession(), MAX_SESSION_CHARS)) {
            return Response.status(400).entity("{\"message\": \"Log is too large\"}").build();
        }

        var existing = logRepository.getLog(log.getUserId(), log.getDate());

        if (existing != null && !existing.isEmpty()) {
            logRepository.updateLog(log.getUserId(), log.getDate(), buildUpdateMap(log, existing));
        } else {
            logRepository.saveLog(LogMapper.toMap(log));
        }

        return Response.ok("{\"message\": \"Log saved successfully!\"}").build();
    }

    private void extractNestedFields(FitLog log) {
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
        log.setChecklistJson(checklistToJson(log.getChecklist()));
    }

    private Map<String, AttributeValue> buildUpdateMap(FitLog log, Map<String, AttributeValue> existing) {
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":s",  value(log.getSession(), existing, "session"));
        values.put(":e",  value(log.getExercises(), existing, "exercises"));
        values.put(":p",  value(log.getProtein(), existing, "protein"));
        values.put(":c",  value(log.getCalories(), existing, "calories"));
        values.put(":w",  value(log.getWater(), existing, "water"));
        values.put(":sl", value(log.getSleep(), existing, "sleep"));
        values.put(":n",  value(log.getNotes(), existing, "notes"));
        values.put(":cl", value(log.getChecklistJson(), existing, "checklist"));
        values.put(":fe", value(log.getFoodEntries(), existing, "foodEntries"));
        values.put(":pd", value(log.getPlannedDay(), existing, "plannedDay"));
        values.put(":rs", value(log.getRest(), existing, "rest"));
        return values;
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** The checklist as a compact JSON object string, or " " (keep what is stored) when absent or unusable. */
    static String checklistToJson(JsonNode node) {
        if (node == null || node.isNull()) return " ";
        try {
            JsonNode parsed = node.isTextual() ? MAPPER.readTree(node.asText()) : node;
            if (parsed == null || !parsed.isObject() || parsed.size() == 0 || parsed.size() > 100) return " ";
            return MAPPER.writeValueAsString(parsed);
        } catch (JsonProcessingException e) {
            return " ";
        }
    }

    static boolean tooLarge(String value, int max) {
        return value != null && value.length() > max;
    }

    /** Returns a weekday name, " " for none, or null when the value is not acceptable. */
    static String normalizePlannedDay(String raw) {
        if (raw == null || raw.isBlank()) return " ";
        String v = raw.trim();
        if (v.length() > 12) return null;
        String cased = v.substring(0, 1).toUpperCase(Locale.ROOT) + v.substring(1).toLowerCase(Locale.ROOT);
        return WEEKDAYS.contains(cased) ? cased : null;
    }

    /** Returns "true", "false", " " for none, or null when the value is not acceptable. */
    static String normalizeRest(String raw) {
        if (raw == null || raw.isBlank()) return " ";
        String v = raw.trim().toLowerCase(Locale.ROOT);
        return v.equals("true") || v.equals("false") ? v : null;
    }

    private AttributeValue value(String newVal, Map<String, AttributeValue> existing, String key) {
        return AttributeValue.fromS(
                newVal != null && !newVal.isBlank()
                        ? newVal
                        : existing.getOrDefault(key, AttributeValue.fromS(" ")).s()
        );
    }

    // GET /logs/{userId}/{date} — gets log for specific date
    @GET
    @Path("/{userId}/{date}")
    public Response getLog(@HeaderParam("Authorization") String authorization, @PathParam("userId") String userId, @PathParam("date") String date) {
        CallerIdentity.requireSelf(authorization, userId);
        var item = logRepository.getLog(userId, date);
        if (item == null || item.isEmpty()) {
            return Response.status(404).build();
        }
        return Response.ok(LogMapper.toLog(item)).build();
    }

    // GET /logs/{userId} — gets all logs for a user
    @GET
    @Path("/{userId}")
    public Response getLogs(@HeaderParam("Authorization") String authorization, @PathParam("userId") String userId) {
        CallerIdentity.requireSelf(authorization, userId);
        var items = logRepository.getLogs(userId);
        var logs = items.stream().map(LogMapper::toLog).collect(Collectors.toList());
        return Response.ok(logs).build();
    }

    @PUT
    @Path("/update/{userId}/{date}")
    public Response updateLog(@HeaderParam("Authorization") String authorization, @PathParam("userId") String userId,
                              @PathParam("date") String date,
                              FitLog log) {
        CallerIdentity.requireSelf(authorization, userId);
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
        String checklist = isValid(log.getChecklistJson()) ? log.getChecklistJson() : existing.getOrDefault("checklist", AttributeValue.fromS(" ")).s();
        String foodEntries = isValid(log.getFoodEntries()) ? log.getFoodEntries() : existing.getOrDefault("foodEntries", AttributeValue.fromS(" ")).s();

        Map<String, AttributeValue> updateValues = new java.util.HashMap<>();
        updateValues.put(":s",  AttributeValue.fromS(session));
        updateValues.put(":e",  AttributeValue.fromS(exercises));
        updateValues.put(":p",  AttributeValue.fromS(protein));
        updateValues.put(":c",  AttributeValue.fromS(calories));
        updateValues.put(":w",  AttributeValue.fromS(water));
        updateValues.put(":sl", AttributeValue.fromS(sleep));
        updateValues.put(":n",  AttributeValue.fromS(notes));
        updateValues.put(":cl", AttributeValue.fromS(checklist));
        updateValues.put(":fe", AttributeValue.fromS(foodEntries));
        updateValues.put(":pd", existing.getOrDefault("plannedDay", AttributeValue.fromS(" ")));
        updateValues.put(":rs", existing.getOrDefault("rest", AttributeValue.fromS(" ")));
        logRepository.updateLog(userId, date, updateValues);

        return Response.ok("{\"message\": \"Log updated successfully!\"}").build();
    }

    /** True when [date] is a real date no later than [today] and at most SKIP_WINDOW_DAYS before it. */
    static boolean isSkippable(String date, LocalDate today) {
        try {
            LocalDate d = LocalDate.parse(date);
            return !d.isAfter(today) && !d.isBefore(today.minusDays(SKIP_WINDOW_DAYS));
        } catch (DateTimeException | NullPointerException e) {
            return false;
        }
    }

    // POST /logs/{userId}/{date}/skip: mark a training day as skipped (or clear the mark).
    @POST
    @Path("/{userId}/{date}/skip")
    public Response skipDay(@HeaderParam("Authorization") String authorization,
                            @PathParam("userId") String userId,
                            @PathParam("date") String date,
                            SkipRequest body) {
        CallerIdentity.requireSelf(authorization, userId);
        if (!isSkippable(date, LocalDate.now(ZoneId.of(zoneId)))) {
            return Response.status(400)
                    .entity("{\"message\": \"Only the last " + SKIP_WINDOW_DAYS + " days can be skipped\"}")
                    .build();
        }
        boolean skipped = body == null || body.getSkipped() == null || body.getSkipped();

        var existing = logRepository.getLog(userId, date);
        if (existing != null && !existing.isEmpty()) {
            logRepository.setSkipped(userId, date, skipped);
        } else if (skipped) {
            FitLog log = new FitLog();
            log.setUserId(userId);
            log.setDate(date);
            log.setDayNumber(body == null ? 0 : Math.max(0, Math.min(body.getDayNumber(), 1000)));
            log.setSkipped("true");
            logRepository.saveLog(LogMapper.toMap(log));
        }
        return Response.ok("{\"skipped\": " + skipped + "}").build();
    }

    private boolean isValid(String value) {
        return value != null && !value.isBlank();
    }
}