package com.fitTrack.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Plain-text digest of one week of logs: workouts, nutrition and checklist adherence. */
public final class WeekSummary {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WeekSummary() {}

    public record Digest(String text, int sessions, List<String> exerciseJson) {}

    public static Digest build(int week, List<Map<String, AttributeValue>> logs, Map<String, ProgressionCalculator.Suggestion> weights) {
        List<String> exerciseJson = new ArrayList<>();
        StringBuilder workouts = new StringBuilder();
        int sessions = 0;
        int nutritionDays = 0;
        double protein = 0, calories = 0, water = 0;
        int waterDays = 0;
        double checklist = 0;
        int checklistDays = 0;

        for (Map<String, AttributeValue> log : logs) {
            String date = s(log, "date");
            String exercises = s(log, "exercises");
            int done = completedSets(exercises);
            if (done > 0) {
                sessions++;
                exerciseJson.add(exercises);
                workouts.append("- ").append(date).append(" ").append(s(log, "session"))
                        .append(": ").append(done).append(" sets done\n");
                workouts.append(compact(exercises)).append("\n");
            }
            double p = num(s(log, "protein")), c = num(s(log, "calories")), w = num(s(log, "water"));
            if (p > 0 || c > 0) {
                nutritionDays++;
                protein += p;
                calories += c;
            }
            if (w > 0) {
                waterDays++;
                water += w;
            }
            double cl = checklistCompletion(s(log, "checklist"));
            if (cl >= 0) {
                checklist += cl;
                checklistDays++;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("WEEK ").append(week).append(" RESULTS\n");
        sb.append("Sessions with completed sets: ").append(sessions).append("\n");
        sb.append(workouts.length() == 0 ? "No workouts logged.\n" : workouts.toString());
        sb.append("\nNUTRITION (days logged: ").append(nutritionDays).append(")\n");
        if (nutritionDays > 0) {
            sb.append("Average protein: ").append(Math.round(protein / nutritionDays)).append(" g/day\n");
            sb.append("Average calories: ").append(Math.round(calories / nutritionDays)).append(" kcal/day\n");
        } else {
            sb.append("No food logged.\n");
        }
        if (waterDays > 0) {
            sb.append("Average water: ").append(Math.round(water / waterDays)).append(" glasses/day\n");
        }
        if (checklistDays > 0) {
            sb.append("Daily checklist completion: ").append(Math.round(100 * checklist / checklistDays)).append("%\n");
        }

        if (!weights.isEmpty()) {
            sb.append("\nNEXT WEEK WORKING WEIGHTS (lb, already computed from the logs above; they are FIXED):\n");
            weights.values().forEach(sg -> sb.append("- ").append(sg.name()).append(": ")
                    .append(ProgressionCalculator.format(sg.weight())).append(" (").append(sg.action()).append(")\n"));
        }
        return new Digest(sb.toString(), sessions, exerciseJson);
    }

    static int completedSets(String exercisesJson) {
        if (exercisesJson == null || exercisesJson.isBlank()) return 0;
        try {
            JsonNode list = MAPPER.readTree(exercisesJson);
            int n = 0;
            for (JsonNode ex : list) {
                for (JsonNode set : ex.path("sets")) {
                    if (set.path("done").asBoolean(false)) n++;
                }
            }
            return n;
        } catch (Exception e) {
            return 0;
        }
    }

    /** Share of checklist items ticked, or -1 when there is no checklist. */
    static double checklistCompletion(String json) {
        if (json == null || json.isBlank()) return -1;
        try {
            JsonNode node = MAPPER.readTree(json);
            if (!node.isObject() || node.size() == 0) return -1;
            int done = 0;
            for (JsonNode v : node) {
                if (v.asBoolean(false) || "true".equalsIgnoreCase(v.asText())) done++;
            }
            return (double) done / node.size();
        } catch (Exception e) {
            return -1;
        }
    }

    private static String compact(String exercisesJson) {
        try {
            StringBuilder sb = new StringBuilder();
            for (JsonNode ex : MAPPER.readTree(exercisesJson)) {
                sb.append("  ").append(ex.path("name").asText("?")).append(": ");
                List<String> sets = new ArrayList<>();
                for (JsonNode set : ex.path("sets")) {
                    if (set.path("done").asBoolean(false)) {
                        sets.add(set.path("weight").asText("?") + "x" + set.path("reps").asText("?"));
                    }
                }
                sb.append(sets.isEmpty() ? "none done" : String.join(", ", sets)).append("\n");
            }
            return sb.toString().stripTrailing();
        } catch (Exception e) {
            return "  (unreadable log)";
        }
    }

    private static double num(String v) {
        try {
            return Double.parseDouble(v.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static String s(Map<String, AttributeValue> m, String k) {
        AttributeValue v = m.get(k);
        return v == null || v.s() == null ? "" : v.s().trim();
    }
}
