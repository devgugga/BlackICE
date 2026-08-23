package dev.blackice.security.application;

/** Provides the current user's access token for server-side calls representing that user. */
public interface AccessTokenProvider {

    String accessToken();
}
