package com.fitTrack.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic weekly weight progression (double progression). The numbers come from what the user
 * actually logged, never from the language model.
 *
 * <p>Per exercise: if every planned set was completed at the working weight and each set reached the
 * top of the rep range, add 5 lb (10 lb for lower-body lifts). Otherwise hold the working weight.
 * With fewer than {@value #MIN_SESSIONS} logged sessions in the week nothing increases.
 */
public final class ProgressionCalculator {

    static final int MIN_SESSIONS = 3;
    static final double UPPER_STEP = 5.0;
    static final double LOWER_STEP = 10.0;

    private static final Pattern RANGE = Pattern.compile("(\\d+)\\s*[-–]\\s*(\\d+)");
    private static final Pattern SINGLE = Pattern.compile("^\\s*(\\d+)\\s*$");
    private static final List<String> LOWER_BODY = List.of(
            "squat", "deadlift", "leg press", "lunge", "hip thrust", "rdl", "romanian", "leg curl", "leg extension");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public enum Action { INCREASE, HOLD }

    public record Suggestion(String name, double weight, Action action) {}

    private ProgressionCalculator() {}

    /**
     * @param lastTraining       the plan's {@code training} object (day -> session with exercises)
     * @param loggedExerciseJson each logged day's {@code exercises} JSON string
     * @param sessionsLogged     number of days with at least one completed set
     * @return suggestions keyed by lower-cased exercise name; exercises with no usable data are omitted
     */
    public static Map<String, Suggestion> nextWeights(
            JsonNode lastTraining, List<String> loggedExerciseJson, int sessionsLogged) {

        Map<String, List<double[]>> doneSets = collectDoneSets(loggedExerciseJson);
        Map<String, Suggestion> out = new LinkedHashMap<>();
        if (lastTraining == null || !lastTraining.isObject()) {
            return out;
        }

        for (JsonNode day : lastTraining) {
            for (JsonNode ex : day.path("exercises")) {
                String name = ex.path("name").asText("").trim();
                if (name.isEmpty()) continue;
                String key = name.toLowerCase(Locale.ROOT);
                if (out.containsKey(key)) continue;

                List<double[]> sets = doneSets.getOrDefault(key, List.of());
                Double previousTarget = parseWeight(ex.path("targetWeight").asText(""));

                if (sets.isEmpty()) {
                    if (previousTarget != null) out.put(key, new Suggestion(name, previousTarget, Action.HOLD));
                    continue;
                }

                double working = sets.stream().mapToDouble(s -> s[0]).max().orElse(0);
                if (working <= 0) continue;

                int plannedSets = ex.path("sets").asInt(0);
                int[] range = parseRepRange(ex.path("reps").asText(""));
                long atWorking = sets.stream().filter(s -> s[0] == working).count();
                boolean topEverySet = range != null
                        && sets.stream().filter(s -> s[0] == working).allMatch(s -> s[1] >= range[1]);

                boolean earned = sessionsLogged >= MIN_SESSIONS
                        && plannedSets > 0
                        && atWorking >= plannedSets
                        && topEverySet;

                if (earned) {
                    out.put(key, new Suggestion(name, working + stepFor(name), Action.INCREASE));
                } else {
                    out.put(key, new Suggestion(name, working, Action.HOLD));
                }
            }
        }
        return out;
    }

    static double stepFor(String exerciseName) {
        String n = exerciseName.toLowerCase(Locale.ROOT);
        return LOWER_BODY.stream().anyMatch(n::contains) ? LOWER_STEP : UPPER_STEP;
    }

    /** Parses "8-10" to {8,10} and "10" to {10,10}. Anything else (AMRAP, "30s") is not progressable. */
    static int[] parseRepRange(String reps) {
        Matcher r = RANGE.matcher(reps);
        if (r.find()) return new int[]{Integer.parseInt(r.group(1)), Integer.parseInt(r.group(2))};
        Matcher s = SINGLE.matcher(reps);
        if (s.find()) {
            int v = Integer.parseInt(s.group(1));
            return new int[]{v, v};
        }
        return null;
    }

    static Double parseWeight(String raw) {
        Matcher m = Pattern.compile("(\\d+(?:\\.\\d+)?)").matcher(raw == null ? "" : raw);
        return m.find() ? Double.valueOf(m.group(1)) : null;
    }

    /** Display form used in the plan: "145" or "142.5". */
    public static String format(double weight) {
        return weight == Math.rint(weight) ? String.valueOf((long) weight) : String.format(Locale.ROOT, "%.1f", weight);
    }

    private static Map<String, List<double[]>> collectDoneSets(List<String> loggedExerciseJson) {
        Map<String, List<double[]>> out = new LinkedHashMap<>();
        for (String raw : loggedExerciseJson) {
            if (raw == null || raw.isBlank()) continue;
            try {
                JsonNode list = MAPPER.readTree(raw);
                if (!list.isArray()) continue;
                for (JsonNode ex : list) {
                    String name = ex.path("name").asText("").trim().toLowerCase(Locale.ROOT);
                    if (name.isEmpty()) continue;
                    for (JsonNode set : ex.path("sets")) {
                        if (!set.path("done").asBoolean(false)) continue;
                        Double w = parseWeight(set.path("weight").asText(""));
                        Double reps = parseWeight(set.path("reps").asText(""));
                        if (w == null || reps == null || w <= 0 || reps <= 0) continue;
                        out.computeIfAbsent(name, k -> new ArrayList<>()).add(new double[]{w, reps});
                    }
                }
            } catch (Exception ignored) {
                // A malformed log day is skipped; it must not block the whole week.
            }
        }
        return out;
    }
}
