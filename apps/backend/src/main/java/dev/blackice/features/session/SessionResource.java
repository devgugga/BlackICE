package dev.blackice.features.session;

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
    @Inject JsonWebToken jwt;   // token da sessao web-app, server-side

    // Guarda do SPA: 401 (nao redireciona) quando anonimo, para o fetch tratar.
    @GET @Path("/me") @PermitAll
    public Response me() {
        if (identity == null || identity.isAnonymous()) {
            return Response.status(401).build();
        }
        var roles = List.copyOf(identity.getRoles());
        // subject = claim `sub` (UUID estavel); username = `preferred_username` (dr.teste).
        // getPrincipal().getName() devolve o `sub`, NAO o username - por isso lemos o claim.
        var subject = jwt.getSubject();
        String username = jwt.getClaim("preferred_username");
        if (username == null) username = subject; // fallback defensivo
        return Response.ok(new SessionResponse(subject, username, roles)).build();
    }

    // Navegacao do browser aqui dispara o redirect OIDC (web-app).
    @GET @Path("/login") @Authenticated
    public Response login() {
        return Response.seeOther(java.net.URI.create("/")).build();
    }
}
