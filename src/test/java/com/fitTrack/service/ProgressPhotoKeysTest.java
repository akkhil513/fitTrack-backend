package com.fitTrack.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressPhotoKeysTest {

    private static final String UID = "11111111-2222-3333-4444-555555555555";
    private static final String RID = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";

    @Test
    void buildsAndParsesAKey() {
        String key = ProgressPhotoKeys.newKey(UID, "2026-11-16", "front", RID);
        assertEquals("progress/" + UID + "/2026-11-16/front/" + RID + ".jpg", key);
        var ref = ProgressPhotoKeys.parse(key).orElseThrow();
        assertEquals(UID, ref.userId());
        assertEquals("2026-11-16", ref.date());
        assertEquals("front", ref.angle());
    }

    @Test
    void rejectsBadInputsWhenBuilding() {
        assertThrows(IllegalArgumentException.class, () -> ProgressPhotoKeys.newKey("../x", "2026-11-16", "front", RID));
        assertThrows(IllegalArgumentException.class, () -> ProgressPhotoKeys.newKey(null, "2026-11-16", "front", RID));
        assertThrows(IllegalArgumentException.class, () -> ProgressPhotoKeys.newKey(UID, "2026-02-30", "front", RID));
        assertThrows(IllegalArgumentException.class, () -> ProgressPhotoKeys.newKey(UID, "1999-01-01", "front", RID));
        assertThrows(IllegalArgumentException.class, () -> ProgressPhotoKeys.newKey(UID, "2026-11-16", "top", RID));
        assertThrows(IllegalArgumentException.class, () -> ProgressPhotoKeys.newKey(UID, "2026-11-16", "front/../x", RID));
        assertThrows(IllegalArgumentException.class, () -> ProgressPhotoKeys.newKey(UID, "2026-11-16", "front", "../../etc"));
    }

    @Test
    void ownershipIsAnExactUserCheck() {
        String key = ProgressPhotoKeys.newKey(UID, "2026-11-16", "side", RID);
        assertTrue(ProgressPhotoKeys.belongsTo(UID, key));
        assertFalse(ProgressPhotoKeys.belongsTo("someone-else", key));
        // A user id that is a prefix of another must not match.
        assertFalse(ProgressPhotoKeys.belongsTo("11111111-2222-3333-4444", key));
        assertFalse(ProgressPhotoKeys.belongsTo(null, key));
        assertFalse(ProgressPhotoKeys.belongsTo(UID, null));
    }

    @Test
    void parseRejectsKeysOutsideTheLayout() {
        assertTrue(ProgressPhotoKeys.parse("profiles/" + UID + "/x.jpg").isEmpty());
        assertTrue(ProgressPhotoKeys.parse("progress/" + UID + "/2026-11-16/front/" + RID + ".png").isEmpty());
        assertTrue(ProgressPhotoKeys.parse("progress/" + UID + "/2026-11-16/front/../" + RID + ".jpg").isEmpty());
        assertTrue(ProgressPhotoKeys.parse("progress/" + UID + "/2026-13-16/front/" + RID + ".jpg").isEmpty());
        assertTrue(ProgressPhotoKeys.parse("progress/" + UID + "/2026-11-16/front/" + RID + ".jpg\n").isEmpty());
        assertTrue(ProgressPhotoKeys.parse("x".repeat(500)).isEmpty());
        assertTrue(ProgressPhotoKeys.parse(null).isEmpty());
    }

    @Test
    void sizeAndContentTypeLimits() {
        ProgressPhotoKeys.requireSize(500_000L);
        assertThrows(IllegalArgumentException.class, () -> ProgressPhotoKeys.requireSize(null));
        assertThrows(IllegalArgumentException.class, () -> ProgressPhotoKeys.requireSize(10L));
        assertThrows(IllegalArgumentException.class, () -> ProgressPhotoKeys.requireSize(ProgressPhotoKeys.MAX_BYTES + 1));
        ProgressPhotoKeys.requireContentType("image/jpeg");
        assertThrows(IllegalArgumentException.class, () -> ProgressPhotoKeys.requireContentType("image/png"));
        assertThrows(IllegalArgumentException.class, () -> ProgressPhotoKeys.requireContentType("text/html"));
        assertThrows(IllegalArgumentException.class, () -> ProgressPhotoKeys.requireContentType(null));
    }

    @Test
    void prefixEndsWithSlashSoSimilarIdsDoNotCollide() {
        assertEquals("progress/" + UID + "/", ProgressPhotoKeys.prefixFor(UID));
    }
}
