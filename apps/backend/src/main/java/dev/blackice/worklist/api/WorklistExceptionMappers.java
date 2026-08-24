package dev.blackice.worklist.api;

import dev.blackice.shared.api.problem.ProblemResponseFactory;
import dev.blackice.shared.api.problem.ApiFailureLogger;
import dev.blackice.shared.api.problem.generated.ProblemType;
import dev.blackice.worklist.application.exception.ArchiveSearchException;
import dev.blackice.worklist.application.exception.InvalidStudySearchException;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Translates the two known Worklist exceptions into catalogued problems.
 *
 * <p>Several internal reasons intentionally converge on one public problem because the catalog
 * describes what the consumer observes, not the Java exception taxonomy. Anything else reaches
 * the shared 500 fallback instead of being disguised as archive unavailability.</p>
 */
public class WorklistExceptionMappers {

    @Inject
    ProblemResponseFactory problems;

    @Inject
    ApiFailureLogger failureLogger;

    @ServerExceptionMapper
    public Response invalidSearch(InvalidStudySearchException exception) {
        ProblemType type = ProblemType.API_SEARCH_INVALID;
        failureLogger.known(type, "GET", "/api/studies", ApiFailureLogger.Reason.INVALID_SEARCH);
        return problems.response(type);
    }

    @ServerExceptionMapper
    public Response archiveSearch(ArchiveSearchException exception) {
        ProblemType type = switch (exception.reason()) {
            case QUERY_TOO_BROAD -> ProblemType.API_SEARCH_TOO_BROAD;
            case INVALID_RESPONSE, HTTP_STATUS -> ProblemType.API_ARCHIVE_RESPONSE_INVALID;
            case TIMEOUT, CONNECTION -> ProblemType.API_ARCHIVE_UNAVAILABLE;
        };
        failureLogger.known(type, "GET", "/api/studies",
            ApiFailureLogger.Reason.valueOf(exception.reason().name()));
        return problems.response(type);
    }
}
