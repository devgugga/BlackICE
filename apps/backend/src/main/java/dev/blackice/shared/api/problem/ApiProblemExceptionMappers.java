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
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Translates REST-layer failures into catalogued problems.
 *
 * <p>Exception messages never reach the client: public text always comes from the catalog. The
 * terminating boundary emits one event containing only the code, status, trace ID, HTTP method
 * and route template. It never records a URI carrying a clinical query.</p>
 */
public class ApiProblemExceptionMappers {

    @Inject
    ProblemResponseFactory problems;

    @Inject
    ApiFailureLogger failureLogger;

    @ServerExceptionMapper
    public Response unauthorized(UnauthorizedException exception, RoutingContext context) {
        return handledHere(context) ? expected(ProblemType.API_AUTHENTICATION_REQUIRED, context) : null;
    }

    @ServerExceptionMapper
    public Response authenticationFailed(AuthenticationFailedException exception, RoutingContext context) {
        return handledHere(context) ? expected(ProblemType.API_AUTHENTICATION_REQUIRED, context) : null;
    }

    @ServerExceptionMapper
    public Response forbidden(ForbiddenException exception, RoutingContext context) {
        return handledHere(context) ? expected(ProblemType.API_ACCESS_DENIED, context) : null;
    }

    @ServerExceptionMapper
    public Response webApplication(WebApplicationException exception, RoutingContext context) {
        if (!handledHere(context)) {
            return exception.getResponse();
        }
        int status = exception.getResponse().getStatus();
        if (status >= 500) {
            return unexpected(exception, context);
        }
        return expected(forStatus(status), context);
    }

    /** Returns false for intentional OIDC login redirects and every route outside {@code /api}. */
    private static boolean handledHere(RoutingContext context) {
        return context != null && ApiJavaScriptRequestChecker.isApiRequest(context);
    }

    /** Converts an unexpected API bug to a catalogued 500 and logs its stack trace once. */
    @ServerExceptionMapper(priority = jakarta.ws.rs.Priorities.USER + 1000)
    public Response unexpected(Throwable exception, RoutingContext context) {
        if (!handledHere(context)) {
            return Response.serverError().build();
        }
        failureLogger.unexpected(context.request().method().name(), "/api/*");
        return problems.response(ProblemType.API_INTERNAL_ERROR);
    }

    private Response expected(ProblemType type, RoutingContext context) {
        failureLogger.known(type, context.request().method().name(), "/api/*", ApiFailureLogger.Reason.REST);
        return problems.response(type);
    }

    /**
     * Returns the catalogued type for a framework status.
     *
     * <p>The REST response filter and Vert.x handler share this mapping so the same table applies
     * regardless of the phase in which the failure originates.</p>
     */
    public static ProblemType forStatus(int status) {
        return switch (status) {
            case 400 -> ProblemType.API_REQUEST_INVALID;
            case 401 -> ProblemType.API_AUTHENTICATION_REQUIRED;
            case 403 -> ProblemType.API_ACCESS_DENIED;
            case 404 -> ProblemType.API_RESOURCE_NOT_FOUND;
            case 405 -> ProblemType.API_METHOD_NOT_ALLOWED;
            case 406 -> ProblemType.API_REPRESENTATION_NOT_ACCEPTABLE;
            case 409 -> ProblemType.API_RESOURCE_CONFLICT;
            case 412 -> ProblemType.API_RESOURCE_VERSION_CONFLICT;
            case 413 -> ProblemType.API_PAYLOAD_TOO_LARGE;
            case 415 -> ProblemType.API_MEDIA_TYPE_UNSUPPORTED;
            default -> status >= 500 ? ProblemType.API_INTERNAL_ERROR : ProblemType.API_REQUEST_INVALID;
        };
    }
}
