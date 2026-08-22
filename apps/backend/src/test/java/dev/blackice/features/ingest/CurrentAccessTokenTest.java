package dev.blackice.features.ingest;

import io.quarkus.oidc.AccessTokenCredential;
import jakarta.ws.rs.NotAuthorizedException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CurrentAccessTokenTest {

    @Test
    void lanca_not_authorized_quando_credencial_for_nula() {
        CurrentAccessToken token = new CurrentAccessToken();
        token.credential = null;
        assertThrows(NotAuthorizedException.class, token::value);
    }

    @Test
    void lanca_not_authorized_quando_token_for_nulo_ou_vazio() {
        CurrentAccessToken token = new CurrentAccessToken();
        token.credential = new AccessTokenCredential(null);
        assertThrows(NotAuthorizedException.class, token::value);

        token.credential = new AccessTokenCredential("   ");
        assertThrows(NotAuthorizedException.class, token::value);
    }

    @Test
    void retorna_token_quando_presente() {
        CurrentAccessToken token = new CurrentAccessToken();
        token.credential = new AccessTokenCredential("valid-token-123");
        assertEquals("valid-token-123", token.value());
    }
}
