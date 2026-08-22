package dev.blackice.features.ingest;

import io.quarkus.security.Authenticated;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

@Path("/api/csrf")
@Authenticated
public class CsrfResource {

    @GET
    public Response create() {
        return Response.noContent().build();
    }
}
