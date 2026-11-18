package com.fitTrack.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Validates and normalises a user's supplement reminders before they are stored.
 *
 * <p>Input is a JSON array. Only the known fields survive, so a client cannot store arbitrary data
 * in the user record. Every error message is a fixed string, safe to return to the caller.
 */
public final class SupplementReminders {

    public static final int MAX_REMINDERS = 20;
    public static final int MAX_LABEL_LENGTH = 80;
    public static final int MAX_RAW_LENGTH = 20_000;

    /** Reminder kinds the app understands. Anything else is rejected. */
    public static final Set<String> KINDS = Set.of("supplement", "workout", "meal", "water", "weighin");
    public static final String DEFAULT_KIND = "supplement";

    private static final Pattern ID = Pattern.compile("^[A-Za-z0-9_-]{1,40}$");
    private static final Pattern TIME = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SupplementReminders() {
    }

    /** Returns the normalised JSON array, or "[]" for blank input. Throws on anything invalid. */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "[]";
        }
        if (raw.length() > MAX_RAW_LENGTH) {
            throw new IllegalArgumentException("Reminders are too large");
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(raw);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Reminders must be valid JSON");
        }
        if (root == null || !root.isArray()) {
            throw new IllegalArgumentException("Reminders must be a list");
        }
        if (root.size() > MAX_REMINDERS) {
            throw new IllegalArgumentException("Too many reminders (maximum " + MAX_REMINDERS + ")");
        }

        ArrayNode out = MAPPER.createArrayNode();
        Set<String> ids = new HashSet<>();
        for (JsonNode item : root) {
            out.add(normalizeOne(item, ids));
        }
        return out.toString();
    }

    private static ObjectNode normalizeOne(JsonNode item, Set<String> ids) {
        if (item == null || !item.isObject()) {
            throw new IllegalArgumentException("Each reminder must be an object");
        }

        String id = text(item, "id");
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid reminder id");
        }
        if (!ids.add(id)) {
            throw new IllegalArgumentException("Duplicate reminder id");
        }

        String time = text(item, "time");
        if (!TIME.matcher(time).matches()) {
            throw new IllegalArgumentException("Time must be HH:mm in 24-hour format");
        }

        String label = text(item, "label").replaceAll("\\p{Cntrl}", "").trim();
        if (label.isEmpty()) {
            throw new IllegalArgumentException("Each reminder needs a name");
        }
        if (label.length() > MAX_LABEL_LENGTH) {
            throw new IllegalArgumentException("Reminder name is too long (maximum " + MAX_LABEL_LENGTH + ")");
        }

        JsonNode daysNode = item.get("days");
        if (daysNode == null || !daysNode.isArray() || daysNode.isEmpty() || daysNode.size() > 7) {
            throw new IllegalArgumentException("Choose at least one day");
        }
        TreeSet<Integer> days = new TreeSet<>();
        for (JsonNode d : daysNode) {
            if (!d.isInt() || d.asInt() < 1 || d.asInt() > 7 || !days.add(d.asInt())) {
                throw new IllegalArgumentException("Days must be unique numbers from 1 (Monday) to 7 (Sunday)");
            }
        }

        ObjectNode normalized = MAPPER.createObjectNode();
        normalized.put("id", id);
        normalized.put("time", time);
        normalized.put("label", label);
        normalized.put("kind", kind(item));
        ArrayNode daysOut = normalized.putArray("days");
        for (int d : days) {
            daysOut.add(d);
        }
        normalized.put("enabled", flag(item, "enabled", true));
        normalized.put("repeat", flag(item, "repeat", false));
        return normalized;
    }

    private static String kind(JsonNode item) {
        JsonNode n = item.get("kind");
        if (n == null || n.isNull()) {
            return DEFAULT_KIND;
        }
        if (!n.isTextual() || !KINDS.contains(n.asText())) {
            throw new IllegalArgumentException("Unknown reminder type");
        }
        return n.asText();
    }

    private static String text(JsonNode item, String field) {
        JsonNode n = item.get(field);
        if (n == null || !n.isTextual()) {
            throw new IllegalArgumentException("Missing or invalid field: " + field);
        }
        return n.asText();
    }

    private static boolean flag(JsonNode item, String field, boolean fallback) {
        JsonNode n = item.get(field);
        if (n == null || n.isNull()) {
            return fallback;
        }
        if (!n.isBoolean()) {
            throw new IllegalArgumentException("Missing or invalid field: " + field);
        }
        return n.asBoolean();
    }
}
