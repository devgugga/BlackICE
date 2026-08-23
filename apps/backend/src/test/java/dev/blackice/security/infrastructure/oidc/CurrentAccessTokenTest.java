package dev.blackice.security.infrastructure.oidc;

import io.quarkus.oidc.AccessTokenCredential;
import jakarta.ws.rs.NotAuthorizedException;
import org.junit.jupiter.api.Test;
import dev.blackice.security.application.AccessTokenProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CurrentAccessTokenTest {

    @Test
    void missing_credential_throws_not_authorized() {
        CurrentAccessToken token = new CurrentAccessToken();
        token.credential = null;
        AccessTokenProvider provider = token;
        assertThrows(NotAuthorizedException.class, provider::accessToken);
    }

    @Test
    void missing_or_blank_token_throws_not_authorized() {
        CurrentAccessToken token = new CurrentAccessToken();
        AccessTokenProvider provider = token;
        token.credential = new AccessTokenCredential(null);
        assertThrows(NotAuthorizedException.class, provider::accessToken);

        token.credential = new AccessTokenCredential("   ");
        assertThrows(NotAuthorizedException.class, provider::accessToken);
    }

    @Test
    void returns_token_when_present() {
        CurrentAccessToken token = new CurrentAccessToken();
        token.credential = new AccessTokenCredential("valid-token-123");
        AccessTokenProvider provider = token;
        assertEquals("valid-token-123", provider.accessToken());
    }
}
