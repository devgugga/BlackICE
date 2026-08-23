package dev.blackice.session.api;

import java.util.List;

/**
 * Public session payload representing the currently authenticated user.
 *
 * @param subject stable OIDC user identifier (from {@code sub} claim)
 * @param username human-readable display username (from {@code preferred_username} claim)
 * @param roles assigned realm security roles
 */
public record SessionResponse(
        String subject,
        String username,
        List<String> roles) {}
