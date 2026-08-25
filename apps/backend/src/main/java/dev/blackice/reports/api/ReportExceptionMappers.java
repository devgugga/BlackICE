package dev.blackice.reports.api;

import dev.blackice.reports.application.exception.ArchiveStudyLookupException;
import dev.blackice.reports.application.exception.InvalidReportRequestException;
import dev.blackice.reports.application.exception.ReportAccessDeniedException;
import dev.blackice.reports.application.exception.ReportConflictException;
import dev.blackice.reports.application.exception.ReportNotFoundException;
import dev.blackice.reports.application.exception.ReportPayloadTooLargeException;
import dev.blackice.reports.application.exception.ReportVersionConflictException;
import dev.blackice.reports.application.exception.StudyNotFoundException;
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

    @ServerExceptionMapper(priority = 1)
    public Response jsonMapping(com.fasterxml.jackson.databind.JsonMappingException exception, RoutingContext context) {
        ProblemType type = ProblemType.API_REQUEST_INVALID;
        failureLogger.known(type, method(context), routeTemplate(context), ApiFailureLogger.Reason.INVALID_REQUEST);
        return problems.response(type);
    }

    @ServerExceptionMapper(priority = 1)
    public Response jsonProcessing(com.fasterxml.jackson.core.JsonProcessingException exception, RoutingContext context) {
        ProblemType type = ProblemType.API_REQUEST_INVALID;
        failureLogger.known(type, method(context), routeTemplate(context), ApiFailureLogger.Reason.INVALID_REQUEST);
        return problems.response(type);
    }

    @ServerExceptionMapper(priority = 10)
    public Response processing(jakarta.ws.rs.ProcessingException exception, RoutingContext context) {
        ProblemType type = ProblemType.API_REQUEST_INVALID;
        failureLogger.known(type, method(context), routeTemplate(context), ApiFailureLogger.Reason.INVALID_REQUEST);
        return problems.response(type);
    }

    @ServerExceptionMapper
    public Response payloadTooLarge(ReportPayloadTooLargeException exception, RoutingContext context) {
        ProblemType type = ProblemType.API_PAYLOAD_TOO_LARGE;
        failureLogger.known(type, method(context), routeTemplate(context), ApiFailureLogger.Reason.INVALID_REQUEST);
        return problems.response(type);
    }

    @ServerExceptionMapper
    public Response studyNotFound(StudyNotFoundException exception, RoutingContext context) {
        ProblemType type = ProblemType.API_RESOURCE_NOT_FOUND;
        failureLogger.known(type, method(context), routeTemplate(context), ApiFailureLogger.Reason.NOT_FOUND);
        return problems.response(type);
    }

    @ServerExceptionMapper
    public Response reportNotFound(ReportNotFoundException exception, RoutingContext context) {
        ProblemType type = ProblemType.API_RESOURCE_NOT_FOUND;
        failureLogger.known(type, method(context), routeTemplate(context), ApiFailureLogger.Reason.NOT_FOUND);
        return problems.response(type);
    }

    @ServerExceptionMapper
    public Response reportConflict(ReportConflictException exception, RoutingContext context) {
        ProblemType type = ProblemType.API_RESOURCE_CONFLICT;
        failureLogger.known(type, method(context), routeTemplate(context), ApiFailureLogger.Reason.CONFLICT);
        return problems.response(type);
    }

    @ServerExceptionMapper
    public Response reportVersionConflict(ReportVersionConflictException exception, RoutingContext context) {
        ProblemType type = ProblemType.API_RESOURCE_VERSION_CONFLICT;
        failureLogger.known(type, method(context), routeTemplate(context), ApiFailureLogger.Reason.VERSION_CONFLICT);
        return problems.response(type);
    }

    @ServerExceptionMapper
    public Response accessDenied(ReportAccessDeniedException exception, RoutingContext context) {
        ProblemType type = ProblemType.API_ACCESS_DENIED;
        failureLogger.known(type, method(context), routeTemplate(context), ApiFailureLogger.Reason.ACCESS_DENIED);
        return problems.response(type);
    }

    @ServerExceptionMapper
    public Response archiveStudyLookup(ArchiveStudyLookupException exception, RoutingContext context) {
        ProblemType type = switch (exception.reason()) {
            case ARCHIVE_AUTH_FAILED -> ProblemType.API_ARCHIVE_RESPONSE_INVALID;
            case ARCHIVE_UNAVAILABLE -> ProblemType.API_ARCHIVE_UNAVAILABLE;
            case ARCHIVE_INVALID_RESPONSE -> ProblemType.API_ARCHIVE_RESPONSE_INVALID;
        };
        ApiFailureLogger.Reason reason = switch (exception.reason()) {
            case ARCHIVE_AUTH_FAILED -> ApiFailureLogger.Reason.AUTHENTICATION;
            case ARCHIVE_UNAVAILABLE -> ApiFailureLogger.Reason.UNAVAILABLE;
            case ARCHIVE_INVALID_RESPONSE -> ApiFailureLogger.Reason.INVALID_RESPONSE;
        };
        failureLogger.known(type, method(context), routeTemplate(context), reason);
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
