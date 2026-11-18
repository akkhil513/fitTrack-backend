package com.fitTrack.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Comparator;
import java.util.Map;

/**
 * A compact, app-friendly summary of the week that just finished, stored on the next week's plan.
 * It is built only from numbers the server computed (sessions logged and the progression results),
 * never from model text, so the app can show it without any further checking.
 */
public final class WeekReport {

    public static final int MAX_CHANGES = 12;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WeekReport() {
    }

    public static String toJson(int week, int sessions, Map<String, ProgressionCalculator.Suggestion> weights) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("week", week);
        root.put("sessions", Math.max(0, sessions));
        ArrayNode changes = root.putArray("changes");
        if (weights != null) {
            weights.values().stream()
                    .sorted(Comparator.comparing(ProgressionCalculator.Suggestion::name, String.CASE_INSENSITIVE_ORDER))
                    .limit(MAX_CHANGES)
                    .forEach(s -> {
                        ObjectNode c = changes.addObject();
                        c.put("name", s.name());
                        c.put("weight", ProgressionCalculator.format(s.weight()));
                        c.put("action", s.action().name());
                    });
        }
        try {
            return MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not write the week report");
        }
    }
}
