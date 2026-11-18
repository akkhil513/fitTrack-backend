package com.fitTrack.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.DateTimeException;
import java.time.LocalDate;

/**
 * Validates and normalises a user's goal targets before they are stored.
 *
 * <p>Input is a JSON object with optional {@code targetWeight} (kg), {@code targetWaist} (cm) and
 * {@code eventDate} (yyyy-MM-dd). Unknown fields are dropped. Error messages are fixed strings.
 */
public final class Goals {

    public static final double MIN_WEIGHT_KG = 20;
    public static final double MAX_WEIGHT_KG = 400;
    public static final double MIN_WAIST_CM = 30;
    public static final double MAX_WAIST_CM = 250;
    public static final int MAX_RAW_LENGTH = 2_000;
    public static final int MIN_YEAR = 2020;
    public static final int MAX_YEAR = 2100;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Goals() {
    }

    /** Returns the normalised JSON object, or "{}" for blank input. Throws on anything invalid. */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "{}";
        }
        if (raw.length() > MAX_RAW_LENGTH) {
            throw new IllegalArgumentException("Goals are too large");
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(raw);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Goals must be valid JSON");
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Goals must be an object");
        }

        ObjectNode out = MAPPER.createObjectNode();
        putNumber(out, root, "targetWeight", MIN_WEIGHT_KG, MAX_WEIGHT_KG, "Target weight must be between 20 and 400 kg");
        putNumber(out, root, "targetWaist", MIN_WAIST_CM, MAX_WAIST_CM, "Target waist must be between 30 and 250 cm");

        JsonNode date = root.get("eventDate");
        if (date != null && !date.isNull()) {
            if (!date.isTextual()) {
                throw new IllegalArgumentException("Event date must be yyyy-MM-dd");
            }
            try {
                LocalDate d = LocalDate.parse(date.asText());
                if (d.getYear() < MIN_YEAR || d.getYear() > MAX_YEAR) {
                    throw new IllegalArgumentException("Event date is out of range");
                }
                out.put("eventDate", d.toString());
            } catch (DateTimeException e) {
                throw new IllegalArgumentException("Event date must be yyyy-MM-dd");
            }
        }
        return out.toString();
    }

    private static void putNumber(ObjectNode out, JsonNode root, String field, double min, double max, String message) {
        JsonNode n = root.get(field);
        if (n == null || n.isNull()) {
            return;
        }
        if (!n.isNumber()) {
            throw new IllegalArgumentException(message);
        }
        double v = n.asDouble();
        if (Double.isNaN(v) || Double.isInfinite(v) || v < min || v > max) {
            throw new IllegalArgumentException(message);
        }
        out.put(field, Math.round(v * 10.0) / 10.0);
    }
}
