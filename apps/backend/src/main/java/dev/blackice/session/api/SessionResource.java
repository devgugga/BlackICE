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

@Path("/api")
public class SessionResource {

    @Inject SecurityIdentity identity;
    @Inject JsonWebToken jwt;   // Server-side token from the web-app session.

    // The SPA needs a 401 instead of an OIDC redirect so its fetch client can handle anonymous access.
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

    // Browser navigation to this endpoint triggers the OIDC redirect in web-app mode.
    @GET @Path("/login") @Authenticated
    public Response login() {
        return Response.seeOther(java.net.URI.create("/")).build();
    }
}
