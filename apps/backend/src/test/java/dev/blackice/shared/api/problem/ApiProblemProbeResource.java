package dev.blackice.shared.api.problem;

import io.vertx.ext.web.Router;
import jakarta.enterprise.event.Observes;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Test-only resource that triggers the HTTP matrix without relying on a product feature. */
@Path("/api/problem-probe")
public class ApiProblemProbeResource {

    /** Registers a Vert.x failure that occurs before Quarkus REST dispatch. */
    void registerPreRestProbe(@Observes Router router) {
        router.get("/api/pre-rest-problem-probe")
            .handler(context -> context.fail(400));
    }

    /** Accepts and returns JSON for malformed 400, 406 and 415 scenarios. */
    @POST
    @Path("/json")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @PermitAll
    public Response json(Payload payload) {
        return Response.ok(payload).build();
    }

    /** Produces only JSON so an incompatible {@code Accept} header becomes 406. */
    @GET
    @Path("/json")
    @Produces(MediaType.APPLICATION_JSON)
    @PermitAll
    public Response json() {
        return Response.ok(new Payload("ok")).build();
    }

    /** Triggers an unexpected failure that must become a catalogued 500 without response leakage. */
    @GET
    @Path("/fail")
    @PermitAll
    public Response fail() {
        IllegalStateException failure = new IllegalStateException(
            "patient-secret: Maria da Silva 1.2.840.10008.1.2",
            new IllegalArgumentException("external-cause patient-secret"));
        failure.addSuppressed(new IllegalStateException("suppressed patient-secret"));
        throw failure;
    }

    /** Mimics an application-level payload refusal such as the ingest limits. */
    @GET
    @Path("/too-large")
    @PermitAll
    public Response tooLarge() {
        return Response.status(Response.Status.REQUEST_ENTITY_TOO_LARGE).build();
    }

    /** Requires the session role so anonymous and wrong-role requests become 401 and 403. */
    @GET
    @Path("/secured")
    @RolesAllowed("auth")
    public Response secured() {
        return Response.noContent().build();
    }

    public record Payload(String value) {
    }
}
