package dev.blackice.viewer.api;

import dev.blackice.shared.api.problem.ApiFailureLogger;
import dev.blackice.shared.api.problem.ProblemResponseFactory;
import dev.blackice.shared.api.problem.generated.ProblemType;
import dev.blackice.viewer.application.exception.ArchiveViewerException;
import dev.blackice.viewer.application.exception.InvalidArchiveMetadataException;
import dev.blackice.viewer.application.exception.InvalidViewerRequestException;
import io.vertx.ext.web.RoutingContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Translates viewer domain and archive exceptions into catalogued RFC 9457 problems.
 *
 * <p>Exception messages never reach the client and are not logged: public details come exclusively
 * from the problem catalog. Route templates are logged instead of concrete URIs to prevent leaking UIDs.</p>
 */
public class ViewerExceptionMappers {

    @Inject
    ProblemResponseFactory problems;

    @Inject
    ApiFailureLogger failureLogger;

    @ServerExceptionMapper
    public Response invalidRequest(InvalidViewerRequestException exception, RoutingContext context) {
        ProblemType type = ProblemType.API_REQUEST_INVALID;
        failureLogger.known(type, method(context), routeTemplate(context), ApiFailureLogger.Reason.INVALID_REQUEST);
        return problems.response(type);
    }

    @ServerExceptionMapper
    public Response invalidArchiveMetadata(InvalidArchiveMetadataException exception, RoutingContext context) {
        ProblemType type = ProblemType.API_ARCHIVE_RESPONSE_INVALID;
        failureLogger.known(type, method(context), routeTemplate(context), ApiFailureLogger.Reason.INVALID_RESPONSE);
        return problems.response(type);
    }

    @ServerExceptionMapper
    public Response archiveViewer(ArchiveViewerException exception, RoutingContext context) {
        ProblemType type = switch (exception.reason()) {
            case AUTHENTICATION -> ProblemType.API_AUTHENTICATION_REQUIRED;
            case ACCESS_DENIED -> ProblemType.API_ACCESS_DENIED;
            case NOT_FOUND -> ProblemType.API_RESOURCE_NOT_FOUND;
            case UNAVAILABLE, TIMEOUT, CONNECTION -> ProblemType.API_ARCHIVE_UNAVAILABLE;
            case INVALID_RESPONSE -> ProblemType.API_ARCHIVE_RESPONSE_INVALID;
        };
        failureLogger.known(type, method(context), routeTemplate(context),
            ApiFailureLogger.Reason.valueOf(exception.reason().name()));
        return problems.response(type);
    }

    private static String method(RoutingContext context) {
        return context != null && context.request() != null && context.request().method() != null
            ? context.request().method().name()
            : "GET";
    }

    private static String routeTemplate(RoutingContext context) {
        if (context != null) {
            String path = context.normalizedPath();
            if (path != null) {
                if (path.contains("/frames/1") || (path.startsWith("/api/dicomweb/") && path.contains("/frames/"))) {
                    return "/api/dicomweb/studies/{studyUid}/series/{seriesUid}/instances/{sopUid}/frames/1";
                }
                if (path.contains("/series/") && path.contains("/instances")) {
                    return "/api/studies/{studyUid}/series/{seriesUid}/instances";
                }
            }
        }
        return "/api/studies/{studyUid}";
    }
}
