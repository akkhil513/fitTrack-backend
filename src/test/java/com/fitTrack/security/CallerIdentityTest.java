package com.fitTrack.security;

import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class CallerIdentityTest {

    private static String bearer(String payloadJson) {
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        String header = enc.encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8));
        String payload = enc.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        return "Bearer " + header + "." + payload + ".signature";
    }

    @Test
    void subjectIsReadFromBearerToken() {
        assertEquals("user-123", CallerIdentity.subject(bearer("{\"sub\":\"user-123\"}")));
    }

    @Test
    void schemeIsCaseInsensitive() {
        String token = bearer("{\"sub\":\"user-123\"}").replaceFirst("Bearer", "bearer");
        assertEquals("user-123", CallerIdentity.subject(token));
    }

    @Test
    void subjectIsNullWhenHeaderMissingOrMalformed() {
        assertNull(CallerIdentity.subject(null));
        assertNull(CallerIdentity.subject(""));
        assertNull(CallerIdentity.subject("Basic abc"));
        assertNull(CallerIdentity.subject("Bearer not-a-jwt"));
        assertNull(CallerIdentity.subject("Bearer a.b.c"));
        assertNull(CallerIdentity.subject(bearer("{\"email\":\"a@b.c\"}")));
    }

    @Test
    void requireSelfAcceptsOwnUserId() {
        assertDoesNotThrow(() ->
                CallerIdentity.requireSelf(bearer("{\"sub\":\"user-123\"}"), "user-123"));
    }

    @Test
    void requireSelfRejectsAnotherUsersId() {
        assertThrows(ForbiddenException.class, () ->
                CallerIdentity.requireSelf(bearer("{\"sub\":\"user-123\"}"), "someone-else"));
    }

    @Test
    void requireSelfRejectsMissingUserId() {
        assertThrows(ForbiddenException.class, () ->
                CallerIdentity.requireSelf(bearer("{\"sub\":\"user-123\"}"), null));
    }

    @Test
    void requireSelfReturns401WithoutToken() {
        assertThrows(NotAuthorizedException.class, () ->
                CallerIdentity.requireSelf(null, "user-123"));
    }

    @Test
    void schedulerRouteRefusesSignedInCallers() {
        assertDoesNotThrow(() -> CallerIdentity.requireNoCaller(null));
        assertDoesNotThrow(() -> CallerIdentity.requireNoCaller(" "));
        assertThrows(ForbiddenException.class, () ->
                CallerIdentity.requireNoCaller(bearer("{\"sub\":\"user-123\"}")));
    }
}
