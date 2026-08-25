package dev.blackice.reports.api;

import dev.blackice.reports.application.exception.InvalidReportRequestException;
import dev.blackice.shared.api.problem.ApiFailureLogger;
import dev.blackice.shared.api.problem.ProblemResponseFactory;
import dev.blackice.shared.api.problem.generated.ProblemType;
import io.vertx.ext.web.RoutingContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Translates report application exceptions into catalogued RFC 9457 problems.
 *
 * <p>Exception messages never reach the client and are not logged: public details come exclusively
 * from the problem catalog. Route templates are logged instead of concrete URIs to prevent leaking UIDs.</p>
 */
public class ReportExceptionMappers {

    private static final String ROUTE_TEMPLATE = "/api/studies/{studyInstanceUid}/report";

    @Inject
    ProblemResponseFactory problems;

    @Inject
    ApiFailureLogger failureLogger;

    @ServerExceptionMapper
    public Response invalidRequest(InvalidReportRequestException exception, RoutingContext context) {
        ProblemType type = ProblemType.API_REQUEST_INVALID;
        failureLogger.known(type, method(context), routeTemplate(context), ApiFailureLogger.Reason.INVALID_REQUEST);
        return problems.response(type);
    }

    private static String method(RoutingContext context) {
        return context != null && context.request() != null && context.request().method() != null
            ? context.request().method().name()
            : "GET";
    }

    private static String routeTemplate(RoutingContext context) {
        return ROUTE_TEMPLATE;
    }
}
