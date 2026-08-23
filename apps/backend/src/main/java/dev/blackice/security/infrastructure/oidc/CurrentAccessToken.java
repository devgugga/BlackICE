package dev.blackice.security.infrastructure.oidc;

import dev.blackice.security.application.AccessTokenProvider;
import io.quarkus.oidc.AccessTokenCredential;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotAuthorizedException;

/**
 * CDI adapter that extracts the raw OIDC access token from the authenticated server-side session.
 *
 * <p>This token contains the mapped audience (e.g. {@code dcm4chee-arc-rs}) allowing the BFF
 * to make authenticated DICOMweb calls representing the logged-in user without exposing the token to JavaScript.</p>
 */
@ApplicationScoped
public class CurrentAccessToken implements AccessTokenProvider {

    @Inject
    AccessTokenCredential credential;

    /**
     * Retrieves the current user's bearer access token string.
     *
     * @return the non-blank access token
     * @throws NotAuthorizedException if no access token credential is present in the current security context
     */
    @Override
    public String accessToken() {
        String token = credential == null ? null : credential.getToken();
        if (token == null || token.isBlank()) {
            throw new NotAuthorizedException("Bearer");
        }
        return token;
    }
}
