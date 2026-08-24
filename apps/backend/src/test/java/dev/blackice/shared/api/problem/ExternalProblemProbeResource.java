package dev.blackice.shared.api.problem;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.ServiceUnavailableException;

/** Exercises REST failures that explicitly sit outside the {@code /api} contract. */
@Path("/outside/problem-probe")
public class ExternalProblemProbeResource {

    @GET
    @Path("/service-unavailable")
    @PermitAll
    public void serviceUnavailable() {
        throw new ServiceUnavailableException();
    }
}
