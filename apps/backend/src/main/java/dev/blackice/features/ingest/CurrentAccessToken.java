package dev.blackice.features.ingest;

import io.quarkus.oidc.AccessTokenCredential;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotAuthorizedException;

@ApplicationScoped
public class CurrentAccessToken {

    @Inject
    AccessTokenCredential credential;

    public String value() {
        String token = credential == null ? null : credential.getToken();
        if (token == null || token.isBlank()) {
            throw new NotAuthorizedException("Bearer");
        }
        return token;
    }
}
