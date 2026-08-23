package dev.blackice.security.api;

import io.quarkus.security.Authenticated;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

/**
 * Authenticated endpoint that triggers the generation of a signed CSRF token cookie.
 *
 * <p>State-mutating BFF endpoints (such as {@code POST /api/studies}) require this token
 * in the {@code X-CSRF-TOKEN} header to prevent Cross-Site Request Forgery with HttpOnly cookies.</p>
 */
@Path("/api/csrf")
@Authenticated
public class CsrfResource {

    /**
     * Returns an empty response (204 No Content) while the Quarkus REST CSRF filter emits the cookie.
     *
     * @return 204 No Content response
     */
    @GET
    public Response create() {
        return Response.noContent().build();
    }
}
