package com.fitTrack.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupplementRemindersTest {

    private static final String VALID =
            "[{\"id\":\"a1\",\"time\":\"08:00\",\"label\":\"Vitamin D3, Omega-3\",\"kind\":\"supplement\",\"days\":[1,2,3,4,5,6,7],"
                    + "\"enabled\":true,\"repeat\":false}]";

    private static void rejects(String json) {
        assertThrows(IllegalArgumentException.class, () -> SupplementReminders.normalize(json), json);
    }

    @Test
    void blankInputMeansNoReminders() {
        assertEquals("[]", SupplementReminders.normalize(null));
        assertEquals("[]", SupplementReminders.normalize("  "));
        assertEquals("[]", SupplementReminders.normalize("[]"));
    }

    @Test
    void validReminderRoundTrips() {
        assertEquals(VALID, SupplementReminders.normalize(VALID));
    }

    @Test
    void unknownFieldsAreDropped() {
        String withJunk = "[{\"id\":\"a1\",\"time\":\"08:00\",\"label\":\"D3\",\"days\":[1],"
                + "\"enabled\":true,\"repeat\":false,\"isAdmin\":true,\"note\":\"<script>\"}]";
        String out = SupplementReminders.normalize(withJunk);
        assertTrue(!out.contains("isAdmin") && !out.contains("script"), out);
    }

    @Test
    void daysAreSortedAndFlagsDefault() {
        String out = SupplementReminders.normalize(
                "[{\"id\":\"a1\",\"time\":\"21:30\",\"label\":\"Magnesium\",\"days\":[5,1,3]}]");
        assertEquals("[{\"id\":\"a1\",\"time\":\"21:30\",\"label\":\"Magnesium\",\"kind\":\"supplement\",\"days\":[1,3,5],"
                + "\"enabled\":true,\"repeat\":false}]", out);
    }

    @Test
    void controlCharactersAreStrippedFromLabels() {
        String out = SupplementReminders.normalize(
                "[{\"id\":\"a1\",\"time\":\"08:00\",\"label\":\"  Iron\\r\\nBEGIN:VEVENT \",\"days\":[1]}]");
        assertTrue(out.contains("\"label\":\"IronBEGIN:VEVENT\""), out);
    }

    @Test
    void rejectsMalformedJsonAndWrongShapes() {
        rejects("not json");
        rejects("{}");
        rejects("[1,2]");
        rejects("[null]");
    }

    @Test
    void rejectsBadTimes() {
        for (String t : new String[]{"8:00", "24:00", "08:60", "08:00 AM", "", "0800"}) {
            rejects("[{\"id\":\"a1\",\"time\":\"" + t + "\",\"label\":\"D3\",\"days\":[1]}]");
        }
    }

    @Test
    void rejectsBadIds() {
        rejects("[{\"id\":\"\",\"time\":\"08:00\",\"label\":\"D3\",\"days\":[1]}]");
        rejects("[{\"id\":\"a b\",\"time\":\"08:00\",\"label\":\"D3\",\"days\":[1]}]");
        rejects("[{\"id\":\"" + "x".repeat(41) + "\",\"time\":\"08:00\",\"label\":\"D3\",\"days\":[1]}]");
        rejects("[{\"id\":\"a1\",\"time\":\"08:00\",\"label\":\"D3\",\"days\":[1]},"
                + "{\"id\":\"a1\",\"time\":\"09:00\",\"label\":\"D4\",\"days\":[1]}]");
    }

    @Test
    void rejectsBadLabels() {
        rejects("[{\"id\":\"a1\",\"time\":\"08:00\",\"label\":\"   \",\"days\":[1]}]");
        rejects("[{\"id\":\"a1\",\"time\":\"08:00\",\"label\":\"" + "x".repeat(81) + "\",\"days\":[1]}]");
        rejects("[{\"id\":\"a1\",\"time\":\"08:00\",\"label\":5,\"days\":[1]}]");
    }

    @Test
    void rejectsBadDays() {
        rejects("[{\"id\":\"a1\",\"time\":\"08:00\",\"label\":\"D3\",\"days\":[]}]");
        rejects("[{\"id\":\"a1\",\"time\":\"08:00\",\"label\":\"D3\",\"days\":[0]}]");
        rejects("[{\"id\":\"a1\",\"time\":\"08:00\",\"label\":\"D3\",\"days\":[8]}]");
        rejects("[{\"id\":\"a1\",\"time\":\"08:00\",\"label\":\"D3\",\"days\":[1,1]}]");
        rejects("[{\"id\":\"a1\",\"time\":\"08:00\",\"label\":\"D3\",\"days\":[\"1\"]}]");
        rejects("[{\"id\":\"a1\",\"time\":\"08:00\",\"label\":\"D3\"}]");
    }

    @Test
    void rejectsNonBooleanFlags() {
        rejects("[{\"id\":\"a1\",\"time\":\"08:00\",\"label\":\"D3\",\"days\":[1],\"enabled\":\"yes\"}]");
        rejects("[{\"id\":\"a1\",\"time\":\"08:00\",\"label\":\"D3\",\"days\":[1],\"repeat\":1}]");
    }

    @Test
    void capsTheNumberOfReminders() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i <= SupplementReminders.MAX_REMINDERS; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"id\":\"r").append(i).append("\",\"time\":\"08:00\",\"label\":\"D\",\"days\":[1]}");
        }
        rejects(sb.append(']').toString());
    }

    @Test
    void rejectsOversizedPayloads() {
        rejects("[" + " ".repeat(SupplementReminders.MAX_RAW_LENGTH) + "]");
    }

    @Test
    void missingKindDefaultsToSupplement() {
        String legacy = "[{\"id\":\"a1\",\"time\":\"08:00\",\"label\":\"Zinc\",\"days\":[1]}]";
        assertTrue(SupplementReminders.normalize(legacy).contains("\"kind\":\"supplement\""));
    }

    @Test
    void acceptsEveryKnownKind() {
        for (String kind : SupplementReminders.KINDS) {
            String json = "[{\"id\":\"a1\",\"time\":\"08:00\",\"label\":\"X\",\"kind\":\"" + kind + "\",\"days\":[1]}]";
            assertTrue(SupplementReminders.normalize(json).contains("\"kind\":\"" + kind + "\""), kind);
        }
    }

    @Test
    void rejectsUnknownOrNonTextKind() {
        rejects("[{\"id\":\"a1\",\"time\":\"08:00\",\"label\":\"X\",\"kind\":\"rm -rf\",\"days\":[1]}]");
        rejects("[{\"id\":\"a1\",\"time\":\"08:00\",\"label\":\"X\",\"kind\":5,\"days\":[1]}]");
    }
}
