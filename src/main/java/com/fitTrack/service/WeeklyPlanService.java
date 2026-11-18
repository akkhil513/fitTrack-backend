package com.fitTrack.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fitTrack.repository.LogRepository;
import com.fitTrack.repository.PlanRepository;
import com.fitTrack.repository.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Builds the next week's plan from what the user actually did. Runs daily; each user is gated so a plan is
 * only generated after the last scheduled training day of the week is done (Saturday or Sunday), or as a
 * catch-up when the stored plan has fallen behind the calendar.
 */
@ApplicationScoped
public class WeeklyPlanService {

    private static final Logger LOG = Logger.getLogger(WeeklyPlanService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject AIService aiService;
    @Inject PlanRepository planRepository;
    @Inject LogRepository logRepository;
    @Inject UserRepository userRepository;

    @ConfigProperty(name = "app.zone", defaultValue = "America/New_York")
    String zoneId;

    public void generateAllUsersWeeklyPlans() {
        LocalDate today = LocalDate.now(ZoneId.of(zoneId));
        String lockKey = "weekly-lock-" + today;

        Map<String, AttributeValue> lock = new HashMap<>();
        lock.put("userId", AttributeValue.fromS(lockKey));
        lock.put("status", AttributeValue.fromS("RUNNING"));
        lock.put("startDate", AttributeValue.fromS(today.toString()));

        if (!planRepository.putIfAbsent(lock)) {
            LOG.infof("Weekly run %s already in progress or done today, exiting", lockKey);
            return;
        }

        try {
            List<Map<String, AttributeValue>> users = userRepository.scanAll();
            LOG.infof("Weekly run: %d users", users.size());
            for (Map<String, AttributeValue> user : users) {
                String userId = getS(user, "userId");
                try {
                    processUser(user, today);
                } catch (Exception e) {
                    LOG.errorf(e, "Weekly plan failed for user %s", userId);
                }
            }
        } finally {
            planRepository.deletePlan(lockKey);
        }
    }

    private void processUser(Map<String, AttributeValue> user, LocalDate today) throws Exception {
        String userId = getS(user, "userId");

        final LocalDate start = parseDate(getS(user, "startDate"));
        if (start == null) {
            return; // no usable start date: nothing to schedule
        }
        int duration = parseIntOr(getS(user, "challengeDuration"), 100);

        Map<String, AttributeValue> currentPlan = planRepository.getPlan(userId);
        if (currentPlan == null || currentPlan.isEmpty() || !"READY".equals(getS(currentPlan, "status"))) {
            return;
        }

        int storedWeek = normalizeStoredWeek(parseIntOr(
                currentPlan.containsKey("weekNumber") ? currentPlan.get("weekNumber").n() : "1", 1));

        JsonNode lastTraining = readJson(getS(currentPlan, "training"));
        OptionalInt target = WeekMath.targetWeek(today, start, storedWeek,
                () -> lastTrainingDayDone(userId, lastTraining, WeekMath.weekStart(start, storedWeek), today));
        if (target.isEmpty()) {
            return;
        }
        int targetWeek = target.getAsInt();

        if (!WeekMath.weekStart(start, targetWeek).isBefore(start.plusDays(duration))) {
            LOG.debugf("User %s challenge complete, no week %d", userId, targetWeek);
            return;
        }

        // Data from the week the stored plan covered.
        LocalDate from = WeekMath.weekStart(start, storedWeek);
        List<Map<String, AttributeValue>> logs =
                logRepository.getLogsBetween(userId, from.toString(), from.plusDays(6).toString());

        // First pass only to learn the session count, then the real digest with the computed weights.
        WeekSummary.Digest probe = WeekSummary.build(storedWeek, logs, Map.of());
        Map<String, ProgressionCalculator.Suggestion> weights =
                ProgressionCalculator.nextWeights(lastTraining, probe.exerciseJson(), probe.sessions());
        WeekSummary.Digest digest = WeekSummary.build(storedWeek, logs, weights);

        String profile = getS(currentPlan, "profile");
        if (profile.isBlank()) {
            profile = buildProfile(user, duration);
        }

        LOG.infof("Generating week %d for %s (%d sessions logged)", targetWeek, userId, digest.sessions());
        String geminiResponse = aiService.generateWeeklyPlan(profile, targetWeek, digest.text());
        JsonNode planNode = MAPPER.readTree(extractJsonText(geminiResponse));

        JsonNode training = planNode.path("training");
        if (training instanceof ObjectNode trainingObject) {
            applyWeights(trainingObject, weights);
        }

        Map<String, AttributeValue> newPlan = new HashMap<>();
        newPlan.put("userId", AttributeValue.fromS(userId));
        newPlan.put("status", AttributeValue.fromS("READY"));
        newPlan.put("weekNumber", AttributeValue.fromN(String.valueOf(targetWeek)));
        newPlan.put("profile", AttributeValue.fromS(profile));
        newPlan.put("weekSummary", AttributeValue.fromS(WeekReport.toJson(storedWeek, digest.sessions(), weights)));
        newPlan.put("strategy", AttributeValue.fromS(planNode.path("strategy").asText("")));
        newPlan.put("training", AttributeValue.fromS(training.toString()));
        newPlan.put("nutrition", AttributeValue.fromS(planNode.path("nutrition").asText("")));
        newPlan.put("supplements", AttributeValue.fromS(planNode.path("supplements").asText("")));
        newPlan.put("recovery", AttributeValue.fromS(planNode.path("recovery").asText("")));
        newPlan.put("dailyChecklist", AttributeValue.fromS(planNode.path("dailyChecklist").toString()));

        planRepository.savePlan(newPlan);
        LOG.infof("Week %d saved for %s", targetWeek, userId);
    }

    /** True when the plan's last non-rest day this week has a log with at least one completed set. */
    private boolean lastTrainingDayDone(String userId, JsonNode training, LocalDate weekMonday, LocalDate today) {
        if (training == null || !training.isObject()) return false;
        List<Map<String, AttributeValue>> logs =
                logRepository.getLogsBetween(userId, weekMonday.toString(), weekMonday.plusDays(6).toString());
        return allSessionsCleared(training, weekMonday, logs);
    }

    /**
     * True when every planned training session of the week has a cleared log. A log covers session D when its
     * plannedDay is D, or when it has no plannedDay and was logged on the date that falls on D. Rest logs never
     * cover a session. Sessions pushed out of the week are dropped by the calendar roll-over, not here.
     */
    static boolean allSessionsCleared(JsonNode training, LocalDate weekMonday, List<Map<String, AttributeValue>> logs) {
        boolean anySession = false;
        for (int i = 0; i < 7; i++) {
            DayOfWeek dow = DayOfWeek.of(i + 1);
            String name = dow.name().charAt(0) + dow.name().substring(1).toLowerCase(Locale.ROOT);
            JsonNode day = training.path(name);
            boolean trains = !day.path("isRestDay").asBoolean(false) && day.path("exercises").size() > 0;
            if (!trains) continue;
            anySession = true;
            String dateOfDay = weekMonday.plusDays(i).toString();
            boolean covered = false;
            for (Map<String, AttributeValue> log : logs) {
                if ("true".equals(getS(log, "rest")) || !dayCleared(log)) continue;
                String planned = getS(log, "plannedDay").trim();
                if (planned.isEmpty() ? dateOfDay.equals(getS(log, "date")) : planned.equals(name)) {
                    covered = true;
                    break;
                }
            }
            if (!covered) return false;
        }
        return anySession;
    }

    /** A training day is cleared when at least one set was completed or the user chose to skip it. */
    static boolean dayCleared(Map<String, AttributeValue> log) {
        if (log == null || log.isEmpty()) return false;
        return "true".equals(getS(log, "skipped")) || WeekSummary.completedSets(getS(log, "exercises")) > 0;
    }

    /** Overwrites targetWeight for exercises we computed; the model's number never wins for those. */
    static void applyWeights(ObjectNode training, Map<String, ProgressionCalculator.Suggestion> weights) {
        for (JsonNode day : training) {
            for (JsonNode ex : day.path("exercises")) {
                if (!(ex instanceof ObjectNode exercise)) continue;
                ProgressionCalculator.Suggestion s =
                        weights.get(exercise.path("name").asText("").trim().toLowerCase(Locale.ROOT));
                if (s != null) {
                    exercise.put("targetWeight", ProgressionCalculator.format(s.weight()));
                }
            }
        }
    }

    static String extractJsonText(String geminiResponse) throws Exception {
        JsonNode text = MAPPER.readTree(geminiResponse)
                .path("candidates").path(0).path("content").path("parts").path(0).path("text");
        if (text.isMissingNode() || text.asText().isBlank()) {
            throw new IllegalStateException("Model response had no text");
        }
        return text.asText().replaceAll("```json\\n?", "").replaceAll("```\\n?", "").trim();
    }

    /** 1-based challenge week; see {@link WeekMath#weekFor}. */
    static int weekFor(LocalDate start, LocalDate today) {
        return WeekMath.weekFor(start, today);
    }

    /** Plans saved before the weekNumber fix carry 0; treat them as week 1. */
    static int normalizeStoredWeek(int stored) {
        return Math.max(1, stored);
    }

    /** Fallback for plans created before the profile was stored; most fields will be "unknown". */
    private String buildProfile(Map<String, AttributeValue> user, int duration) {
        return String.format(
                "Gender: %s, Age: %s, Weight: %s, Goal: %s, Training level: %s, Gym access: %s, "
                        + "Days per week: %s, Diet: %s, Challenge duration: %s days",
                getS(user, "gender"), getS(user, "age"), getS(user, "weight"), getS(user, "primaryGoal"),
                getS(user, "trainingLevel"), getS(user, "gymAccess"), getS(user, "daysPerWeek"),
                getS(user, "dietType"), duration);
    }

    private static LocalDate parseDate(String raw) {
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static JsonNode readJson(String raw) {
        try {
            return raw == null || raw.isBlank() ? null : MAPPER.readTree(raw);
        } catch (Exception e) {
            return null;
        }
    }

    private static int parseIntOr(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String getS(Map<String, AttributeValue> map, String key) {
        AttributeValue val = map.get(key);
        return val == null || val.s() == null ? "" : val.s().trim();
    }
}
