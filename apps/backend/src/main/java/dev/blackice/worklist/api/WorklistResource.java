package dev.blackice.worklist.api;

import dev.blackice.security.application.AccessTokenProvider;
import dev.blackice.worklist.application.exception.ArchiveSearchException;
import dev.blackice.worklist.application.exception.InvalidStudySearchException;
import dev.blackice.worklist.application.input.StudySearchRequest;
import dev.blackice.worklist.application.result.StudyPage;
import dev.blackice.worklist.application.usecase.SearchStudiesUseCase;
import io.smallrye.common.annotation.Blocking;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.UUID;

/**
 * HTTP boundary for querying DICOM studies authenticated by the BFF session.
 */
@Path("/api/studies")
@RolesAllowed("auth")
public class WorklistResource {

    private static final Logger LOG = Logger.getLogger(WorklistResource.class);

    private final SearchStudiesUseCase useCase;
    private final AccessTokenProvider accessTokenProvider;

    @Inject
    public WorklistResource(SearchStudiesUseCase useCase, AccessTokenProvider accessTokenProvider) {
        this.useCase = Objects.requireNonNull(useCase, "useCase must not be null");
        this.accessTokenProvider = Objects.requireNonNull(accessTokenProvider, "accessTokenProvider must not be null");
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Blocking
    public Response search(
        @QueryParam("patientName") String patientName,
        @QueryParam("patientId") String patientId,
        @QueryParam("modality") String modality,
        @QueryParam("dateFrom") String dateFrom,
        @QueryParam("dateTo") String dateTo,
        @DefaultValue("20") @QueryParam("limit") int limit,
        @DefaultValue("0") @QueryParam("offset") int offset
    ) {
        String requestId = UUID.randomUUID().toString();
        long started = System.nanoTime();
        MDC.put("requestId", requestId);
        try {
            StudySearchRequest request = request(patientName, patientId, modality, dateFrom, dateTo, limit, offset);
            StudyPage page = useCase.search(request, accessTokenProvider.accessToken());
            LOG.infov("worklist search finished: limit={0}, offset={1}, hasPatientName={2}, hasPatientId={3}, hasModality={4}, hasDateRange={5}, results={6}, durationMs={7}",
                limit, offset, patientName != null, patientId != null, modality != null,
                dateFrom != null || dateTo != null, page.items().size(), elapsedMillis(started));
            return Response.ok(page).header("X-Request-ID", requestId).build();
        } catch (InvalidStudySearchException | DateTimeParseException exception) {
            return error(400, "INVALID_SEARCH", "Review the supplied search filters.", requestId);
        } catch (ArchiveSearchException exception) {
            return archiveError(exception.reason(), requestId);
        } finally {
            MDC.remove("requestId");
        }
    }

    private StudySearchRequest request(
        String patientName,
        String patientId,
        String modality,
        String dateFrom,
        String dateTo,
        int limit,
        int offset
    ) {
        LocalDate parsedDateFrom = parseDate(dateFrom);
        LocalDate parsedDateTo = parseDate(dateTo);
        return new StudySearchRequest(patientName, patientId, modality, parsedDateFrom, parsedDateTo, limit, offset);
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        return LocalDate.parse(dateStr.strip());
    }

    private Response error(int status, String code, String message, String requestId) {
        return Response.status(status)
            .header("X-Request-ID", requestId)
            .entity(new WorklistErrorResponse(code, message))
            .build();
    }

    private Response archiveError(ArchiveSearchException.Reason reason, String requestId) {
        return switch (reason) {
            case QUERY_TOO_BROAD -> error(413, "SEARCH_TOO_BROAD", "The query matches too many studies. Refine your search filters.", requestId);
            case INVALID_RESPONSE, HTTP_STATUS -> error(502, "ARCHIVE_INVALID_RESPONSE", "The archive returned an unexpected response.", requestId);
            case TIMEOUT, CONNECTION -> error(503, "ARCHIVE_UNAVAILABLE", "The archive is currently unavailable.", requestId);
        };
    }

    private static long elapsedMillis(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
    }
}
