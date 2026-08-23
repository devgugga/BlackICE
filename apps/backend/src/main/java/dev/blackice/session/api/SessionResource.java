package dev.blackice.session.api;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * BFF session endpoints providing authentication state and login redirection for the SPA.
 */
@Path("/api")
public class SessionResource {

    @Inject SecurityIdentity identity;
    @Inject JsonWebToken jwt;

    /**
     * Checks the current session authentication status.
     *
     * <p>Returns 401 Unauthorized for anonymous callers (instead of an automatic OIDC redirect)
     * so that the SPA route guard can programmatically decide navigation, and returns user metadata when authenticated.</p>
     *
     * @return 200 OK with {@link SessionResponse} if authenticated, or 401 Unauthorized if anonymous
     */
    @GET @Path("/me") @PermitAll
    public Response me() {
        if (identity == null || identity.isAnonymous()) {
            return Response.status(401).build();
        }
        var roles = List.copyOf(identity.getRoles());
        // `sub` is the stable identity; `preferred_username` is the display name expected by the SPA.
        // getPrincipal().getName() returns `sub`, so the username must be read from its dedicated claim.
        var subject = jwt.getSubject();
        String username = jwt.getClaim("preferred_username");
        if (username == null) username = subject; // Defensive fallback for tokens without a display name.
        return Response.ok(new SessionResponse(subject, username, roles)).build();
    }

    /**
     * Initiates the OIDC authorization code flow in web-app mode and redirects to the SPA on success.
     *
     * @return 303 See Other redirect to {@code /}
     */
    @GET @Path("/login") @Authenticated
    public Response login() {
        return Response.seeOther(java.net.URI.create("/")).build();
    }
}
