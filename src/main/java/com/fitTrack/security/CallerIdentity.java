package com.fitTrack.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;

import java.util.Base64;

/**
 * Per-user access checks.
 *
 * API Gateway's JWT authorizer verifies the Cognito ID token (signature, issuer,
 * audience, expiry) before the request reaches this Lambda. This class only reads
 * the already-verified {@code sub} claim and makes sure it matches the userId the
 * request is trying to read or write. It deliberately does not re-verify the
 * signature, so the Lambda must never be reachable except through the gateway
 * (the public Function URL has been removed from the template for that reason).
 */
public final class CallerIdentity {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String BEARER = "Bearer ";

    private CallerIdentity() {
    }

    /** Returns the {@code sub} claim of the bearer token, or null if absent or unreadable. */
    public static String subject(String authorizationHeader) {
        if (authorizationHeader == null
                || !authorizationHeader.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            return null;
        }
        String[] parts = authorizationHeader.substring(BEARER.length()).trim().split("\\.");
        if (parts.length != 3) {
            return null;
        }
        try {
            JsonNode payload = MAPPER.readTree(Base64.getUrlDecoder().decode(parts[1]));
            String sub = payload.path("sub").asText("");
            return sub.isEmpty() ? null : sub;
        } catch (Exception e) {
            return null;
        }
    }

    /** 401 when there is no readable token, 403 when the token belongs to someone else. */
    public static void requireSelf(String authorizationHeader, String userId) {
        String sub = subject(authorizationHeader);
        if (sub == null) {
            throw new NotAuthorizedException("Bearer");
        }
        if (userId == null || !sub.equals(userId)) {
            throw new ForbiddenException();
        }
    }

    /**
     * For routes that only the scheduler may call. EventBridge invokes the Lambda
     * directly and never sends an Authorization header; any request that carries
     * one came through the gateway from a signed-in user and is refused.
     */
    public static void requireNoCaller(String authorizationHeader) {
        if (authorizationHeader != null && !authorizationHeader.isBlank()) {
            throw new ForbiddenException();
        }
    }
}
