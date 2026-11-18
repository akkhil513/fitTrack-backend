package com.fitTrack.service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds and checks the S3 object keys for progress photos.
 *
 * <p>Layout: {@code progress/<userId>/<yyyy-MM-dd>/<angle>/<uuid>.jpg}. The key carries all the
 * metadata the app needs, so no database table is required, and every key is rooted at the owner's
 * id so ownership is a prefix check.
 */
public final class ProgressPhotoKeys {

    public static final Set<String> ANGLES = Set.of("front", "side", "back");
    public static final String CONTENT_TYPE = "image/jpeg";
    public static final long MAX_BYTES = 2_000_000L;
    public static final long MIN_BYTES = 1_000L;
    public static final int MAX_PHOTOS = 300;
    public static final int MIN_YEAR = 2020;
    public static final int MAX_YEAR = 2100;

    private static final Pattern USER_ID = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
    private static final Pattern KEY = Pattern.compile(
            "^progress/([A-Za-z0-9_-]{1,64})/(\\d{4}-\\d{2}-\\d{2})/(front|side|back)/([0-9a-f-]{36})\\.jpg$");

    private ProgressPhotoKeys() {
    }

    /** A parsed photo key. */
    public record PhotoRef(String key, String userId, String date, String angle) {
    }

    public static boolean isValidUserId(String userId) {
        return userId != null && USER_ID.matcher(userId).matches();
    }

    /** The prefix under which all of a user's photos live (ends with a slash). */
    public static String prefixFor(String userId) {
        requireUserId(userId);
        return "progress/" + userId + "/";
    }

    /** Validates the inputs and returns a new unique key. Throws IllegalArgumentException. */
    public static String newKey(String userId, String date, String angle, String randomId) {
        requireUserId(userId);
        String d = validDate(date);
        if (angle == null || !ANGLES.contains(angle)) {
            throw new IllegalArgumentException("Angle must be front, side or back");
        }
        if (randomId == null || !randomId.matches("^[0-9a-f-]{36}$")) {
            throw new IllegalArgumentException("Invalid photo id");
        }
        return "progress/" + userId + "/" + d + "/" + angle + "/" + randomId + ".jpg";
    }

    public static String validDate(String date) {
        if (date == null) {
            throw new IllegalArgumentException("Date must be yyyy-MM-dd");
        }
        try {
            LocalDate d = LocalDate.parse(date);
            if (d.getYear() < MIN_YEAR || d.getYear() > MAX_YEAR) {
                throw new IllegalArgumentException("Date is out of range");
            }
            return d.toString();
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Date must be yyyy-MM-dd");
        }
    }

    public static void requireSize(Long size) {
        if (size == null || size < MIN_BYTES || size > MAX_BYTES) {
            throw new IllegalArgumentException("Photo must be between 1 KB and 2 MB");
        }
    }

    public static void requireContentType(String contentType) {
        if (!CONTENT_TYPE.equals(contentType)) {
            throw new IllegalArgumentException("Only JPEG photos are accepted");
        }
    }

    /** Parses a key, returning empty for anything that does not match the layout exactly. */
    public static Optional<PhotoRef> parse(String key) {
        if (key == null || key.length() > 200) {
            return Optional.empty();
        }
        Matcher m = KEY.matcher(key);
        if (!m.matches()) {
            return Optional.empty();
        }
        try {
            LocalDate.parse(m.group(2));
        } catch (DateTimeException e) {
            return Optional.empty();
        }
        return Optional.of(new PhotoRef(key, m.group(1), m.group(2), m.group(3)));
    }

    /** True only when the key is well-formed and rooted at this user's own prefix. */
    public static boolean belongsTo(String userId, String key) {
        if (!isValidUserId(userId)) {
            return false;
        }
        return parse(key).map(r -> r.userId().equals(userId)).orElse(false);
    }

    private static void requireUserId(String userId) {
        if (!isValidUserId(userId)) {
            throw new IllegalArgumentException("Invalid user id");
        }
    }
}
