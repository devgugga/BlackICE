package dev.blackice.shared.api.problem;

import dev.blackice.security.api.ApiJavaScriptRequestChecker;
import dev.blackice.shared.api.problem.generated.ProblemType;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import io.vertx.ext.web.RoutingContext;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Traduz falhas da camada REST em problemas catalogados.
 *
 * <p>Nenhuma mensagem de exceção chega ao cliente: os textos vêm sempre do
 * catálogo. O log acontece uma única vez, aqui, e carrega apenas code, status,
 * método e template de rota — nunca a URI com query, que leva a busca clínica.
 */
public class ApiProblemExceptionMappers {

    private static final Logger LOG = Logger.getLogger(ApiProblemExceptionMappers.class);

    @Inject
    ProblemResponseFactory problems;

    @ServerExceptionMapper
    public Response unauthorized(UnauthorizedException exception, RoutingContext context) {
        return handledHere(context) ? expected(ProblemType.API_AUTHENTICATION_REQUIRED) : null;
    }

    @ServerExceptionMapper
    public Response authenticationFailed(AuthenticationFailedException exception, RoutingContext context) {
        return handledHere(context) ? expected(ProblemType.API_AUTHENTICATION_REQUIRED) : null;
    }

    @ServerExceptionMapper
    public Response forbidden(ForbiddenException exception, RoutingContext context) {
        return handledHere(context) ? expected(ProblemType.API_ACCESS_DENIED) : null;
    }

    @ServerExceptionMapper
    public Response webApplication(WebApplicationException exception, RoutingContext context) {
        int status = exception.getResponse().getStatus();
        if (status >= 500) {
            return unexpected(exception);
        }
        return handledHere(context) ? expected(forStatus(status)) : null;
    }

    /**
     * Falso para {@code /api/login}, cujo redirect OIDC e intencional, e para
     * qualquer rota fora de {@code /api}, que nao pertence a este contrato.
     */
    private static boolean handledHere(RoutingContext context) {
        return context == null || ApiJavaScriptRequestChecker.isApiRequest(context);
    }

    /** Fallback: qualquer bug vira 500 catalogado, com stack trace só no log. */
    @ServerExceptionMapper(priority = jakarta.ws.rs.Priorities.USER + 1000)
    public Response unexpected(Throwable exception) {
        LOG.error("falha inesperada na fronteira /api", exception);
        return problems.response(ProblemType.API_INTERNAL_ERROR);
    }

    private Response expected(ProblemType type) {
        LOG.infov("falha esperada na fronteira /api: code={0}, status={1}",
            type.code(), type.httpStatus());
        return problems.response(type);
    }

    /**
     * Tipo catalogado de um status produzido pelo framework.
     *
     * <p>Compartilhado com o filtro de resposta e com o handler Vert.x, para que
     * a mesma tabela decida em qualquer fase em que a falha nasça.
     */
    public static ProblemType forStatus(int status) {
        return switch (status) {
            case 400 -> ProblemType.API_REQUEST_INVALID;
            case 401 -> ProblemType.API_AUTHENTICATION_REQUIRED;
            case 403 -> ProblemType.API_ACCESS_DENIED;
            case 404 -> ProblemType.API_RESOURCE_NOT_FOUND;
            case 405 -> ProblemType.API_METHOD_NOT_ALLOWED;
            case 406 -> ProblemType.API_REPRESENTATION_NOT_ACCEPTABLE;
            case 413 -> ProblemType.API_PAYLOAD_TOO_LARGE;
            case 415 -> ProblemType.API_MEDIA_TYPE_UNSUPPORTED;
            default -> status >= 500 ? ProblemType.API_INTERNAL_ERROR : ProblemType.API_REQUEST_INVALID;
        };
    }
}
