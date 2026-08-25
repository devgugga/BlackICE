package dev.blackice.reports.api;

import dev.blackice.reports.application.input.ReportActor;
import dev.blackice.reports.application.input.ReportStudyRef;
import dev.blackice.reports.application.result.StudyReportResult;
import dev.blackice.reports.application.usecase.GetStudyReportUseCase;
import io.smallrye.common.annotation.Blocking;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * HTTP boundary for study reports authenticated by the BFF session.
 *
 * <p>Protected by the BFF session. GET requests query only the local product PostgreSQL database,
 * never contacting the DICOM archive. Responses never leak clinical identifiers or authorization tokens
 * in logs and always set {@code Cache-Control: no-store}.</p>
 */
@Path("/api/studies")
@RolesAllowed("auth")
public class ReportResource {

    private static final Logger LOG = Logger.getLogger(ReportResource.class);
    private static final String ROUTE_TEMPLATE = "/api/studies/{studyInstanceUid}/report";

    private final GetStudyReportUseCase getStudyReportUseCase;
    private final CurrentReportActor currentReportActor;
    private final ReportRepresentationMapper responseMapper;

    @Inject
    public ReportResource(
        GetStudyReportUseCase getStudyReportUseCase,
        CurrentReportActor currentReportActor,
        ReportRepresentationMapper responseMapper
    ) {
        this.getStudyReportUseCase = Objects.requireNonNull(getStudyReportUseCase, "getStudyReportUseCase must not be null");
        this.currentReportActor = Objects.requireNonNull(currentReportActor, "currentReportActor must not be null");
        this.responseMapper = Objects.requireNonNull(responseMapper, "responseMapper must not be null");
    }

    @GET
    @Path("/{studyInstanceUid}/report")
    @Produces(MediaType.APPLICATION_JSON)
    @Blocking
    public Response getReport(@PathParam("studyInstanceUid") String studyInstanceUid) {
        long started = System.nanoTime();
        ReportStudyRef studyRef = new ReportStudyRef(studyInstanceUid);
        ReportActor actor = currentReportActor.actor();

        Optional<StudyReportResult> result = getStudyReportUseCase.execute(studyRef, actor);

        LOG.infov("study report get completed: route={0}, found={1}, durationMs={2}",
            ROUTE_TEMPLATE, result.isPresent(), elapsedMillis(started));

        if (result.isEmpty()) {
            return baseResponse(Response.Status.NO_CONTENT).build();
        }

        StudyReportResult report = result.get();
        ReportResponse responseBody = responseMapper.toResponse(report);
        EntityTag etag = ReportEtag.fromVersion(report.version());

        return baseResponse(Response.Status.OK)
            .entity(responseBody)
            .tag(etag)
            .build();
    }

    private static Response.ResponseBuilder baseResponse(Response.Status status) {
        return Response.status(status).header(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    private static long elapsedMillis(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
    }
}
