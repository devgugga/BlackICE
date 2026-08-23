package dev.blackice.security.infrastructure.oidc;

import dev.blackice.security.application.AccessTokenProvider;
import io.quarkus.oidc.AccessTokenCredential;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotAuthorizedException;

@ApplicationScoped
public class CurrentAccessToken implements AccessTokenProvider {

    @Inject
    AccessTokenCredential credential;

    @Override
    public String accessToken() {
        String token = credential == null ? null : credential.getToken();
        if (token == null || token.isBlank()) {
            throw new NotAuthorizedException("Bearer");
        }
        return token;
    }
}
