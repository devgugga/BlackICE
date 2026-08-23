package dev.blackice.worklist.api;

import dev.blackice.security.application.AccessTokenProvider;
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

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Objects;

/**
 * HTTP boundary for querying DICOM studies authenticated by the BFF session.
 *
 * <p>Failures are not handled here: {@link WorklistExceptionMappers} translates
 * the two known application exceptions into catalogued problems, and anything
 * unexpected falls through to the shared boundary. Correlation is carried by the
 * W3C trace context, so no identifier is created in this class.
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
        long started = System.nanoTime();
        StudySearchRequest request = request(patientName, patientId, modality, dateFrom, dateTo, limit, offset);
        StudyPage page = useCase.search(request, accessTokenProvider.accessToken());

        LOG.infov("worklist search finished: limit={0}, offset={1}, hasPatientName={2}, hasPatientId={3}, hasModality={4}, hasDateRange={5}, results={6}, durationMs={7}",
            limit, offset, patientName != null, patientId != null, modality != null,
            dateFrom != null || dateTo != null, page.items().size(), elapsedMillis(started));
        return Response.ok(page).build();
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
        LocalDate parsedDateFrom = parseDate(dateFrom, "dateFrom");
        LocalDate parsedDateTo = parseDate(dateTo, "dateTo");
        return new StudySearchRequest(patientName, patientId, modality, parsedDateFrom, parsedDateTo, limit, offset);
    }

    /**
     * Parses an ISO date filter.
     *
     * <p>A malformed value is a rejected search filter, not a transport failure,
     * so it is converted here into the application exception the mapper knows.
     * The offending value is never echoed back: it comes from a clinical query.
     */
    private LocalDate parseDate(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.strip());
        } catch (DateTimeParseException malformed) {
            throw new InvalidStudySearchException(field + " is not a valid ISO date", malformed);
        }
    }

    private static long elapsedMillis(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
    }
}
