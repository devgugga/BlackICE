package dev.blackice.shared.api.problem;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Recurso exclusivo de teste que provoca cada falha da matriz HTTP.
 *
 * <p>Ele existe para exercitar a fronteira de erro sem depender do
 * comportamento de uma feature real, e por isso vive apenas em
 * {@code src/test}.
 */
@Path("/api/problem-probe")
public class ApiProblemProbeResource {

    /** Aceita e devolve JSON: usado para 400 malformado, 406 e 415. */
    @POST
    @Path("/json")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @PermitAll
    public Response json(Payload payload) {
        return Response.ok(payload).build();
    }

    /** Só produz JSON: um {@code Accept} incompatível cai em 406. */
    @GET
    @Path("/json")
    @Produces(MediaType.APPLICATION_JSON)
    @PermitAll
    public Response json() {
        return Response.ok(new Payload("ok")).build();
    }

    /** Falha inesperada: precisa virar 500 catalogado, sem vazar a mensagem. */
    @GET
    @Path("/fail")
    @PermitAll
    public Response fail() {
        throw new IllegalStateException("patient-secret: Maria da Silva 1.2.840.10008.1.2");
    }

    /** Recusa por limite da aplicação, como fazem os limites da ingestão. */
    @GET
    @Path("/too-large")
    @PermitAll
    public Response tooLarge() {
        return Response.status(Response.Status.REQUEST_ENTITY_TOO_LARGE).build();
    }

    /** Exige a role de sessão: anônimo vira 401 e role errada vira 403. */
    @GET
    @Path("/secured")
    @RolesAllowed("auth")
    public Response secured() {
        return Response.noContent().build();
    }

    public record Payload(String value) {
    }
}
