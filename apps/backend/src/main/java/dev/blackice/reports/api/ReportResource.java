package dev.blackice.reports.api;

import dev.blackice.reports.application.exception.InvalidReportRequestException;
import dev.blackice.reports.application.input.CreateReportCommand;
import dev.blackice.reports.application.input.ReportActor;
import dev.blackice.reports.application.input.ReportContent;
import dev.blackice.reports.application.input.ReportStudyRef;
import dev.blackice.reports.application.input.UpdateReportCommand;
import dev.blackice.reports.application.result.StudyReportResult;
import dev.blackice.reports.application.usecase.CreateStudyReportUseCase;
import dev.blackice.reports.application.usecase.GetStudyReportUseCase;
import dev.blackice.reports.application.usecase.UpdateStudyReportUseCase;
import dev.blackice.reports.domain.ReportStatus;
import dev.blackice.security.application.AccessTokenProvider;
import io.smallrye.common.annotation.Blocking;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * HTTP boundary for study reports authenticated by the BFF session.
 *
 * <p>Protected by the BFF session. GET requests query only the local product PostgreSQL database,
 * never contacting the DICOM archive. POST requests validate study existence in the archive via QIDO
 * before creating the report. PUT requests mutate existing reports with optimistic concurrency via ETag
 * precondition and never contact the DICOM archive. Responses never leak clinical identifiers or
 * authorization tokens in logs and always set {@code Cache-Control: no-store}.</p>
 */
@Path("/api/studies")
@RolesAllowed("auth")
public class ReportResource {

    private static final Logger LOG = Logger.getLogger(ReportResource.class);
    private static final String ROUTE_TEMPLATE = "/api/studies/{studyInstanceUid}/report";

    private final GetStudyReportUseCase getStudyReportUseCase;
    private final CreateStudyReportUseCase createStudyReportUseCase;
    private final UpdateStudyReportUseCase updateStudyReportUseCase;
    private final CurrentReportActor currentReportActor;
    private final AccessTokenProvider accessTokenProvider;
    private final ReportRepresentationMapper responseMapper;

    @Inject
    public ReportResource(
        GetStudyReportUseCase getStudyReportUseCase,
        CreateStudyReportUseCase createStudyReportUseCase,
        UpdateStudyReportUseCase updateStudyReportUseCase,
        CurrentReportActor currentReportActor,
        AccessTokenProvider accessTokenProvider,
        ReportRepresentationMapper responseMapper
    ) {
        this.getStudyReportUseCase = Objects.requireNonNull(getStudyReportUseCase, "getStudyReportUseCase must not be null");
        this.createStudyReportUseCase = Objects.requireNonNull(createStudyReportUseCase, "createStudyReportUseCase must not be null");
        this.updateStudyReportUseCase = Objects.requireNonNull(updateStudyReportUseCase, "updateStudyReportUseCase must not be null");
        this.currentReportActor = Objects.requireNonNull(currentReportActor, "currentReportActor must not be null");
        this.accessTokenProvider = Objects.requireNonNull(accessTokenProvider, "accessTokenProvider must not be null");
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

    @POST
    @Path("/{studyInstanceUid}/report")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Blocking
    public Response createReport(
        @PathParam("studyInstanceUid") String studyInstanceUid,
        ReportRequest request
    ) {
        long started = System.nanoTime();
        if (request == null || request.content() == null || request.status() == null) {
            throw new InvalidReportRequestException();
        }

        ReportStatus status;
        try {
            status = ReportStatus.valueOf(request.status());
        } catch (IllegalArgumentException e) {
            throw new InvalidReportRequestException();
        }

        ReportStudyRef studyRef = new ReportStudyRef(studyInstanceUid);
        ReportActor actor = currentReportActor.actor();
        ReportContent content = new ReportContent(request.content());
        String token = accessTokenProvider.accessToken();

        CreateReportCommand command = new CreateReportCommand(
            studyRef,
            actor,
            content,
            status,
            token
        );

        StudyReportResult report = createStudyReportUseCase.execute(command);

        LOG.infov("study report create completed: route={0}, status={1}, durationMs={2}",
            ROUTE_TEMPLATE, report.status(), elapsedMillis(started));

        ReportResponse responseBody = responseMapper.toResponse(report);
        EntityTag etag = ReportEtag.fromVersion(report.version());
        URI location = URI.create("/api/studies/" + studyRef.studyInstanceUid() + "/report");

        return baseResponse(Response.Status.CREATED)
            .location(location)
            .entity(responseBody)
            .tag(etag)
            .build();
    }

    @PUT
    @Path("/{studyInstanceUid}/report")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Blocking
    public Response updateReport(
        @PathParam("studyInstanceUid") String studyInstanceUid,
        @HeaderParam("If-Match") String ifMatchHeader,
        ReportRequest request
    ) {
        long started = System.nanoTime();
        if (request == null || request.content() == null || request.status() == null) {
            throw new InvalidReportRequestException();
        }

        long expectedVersion = ReportEtag.parseStrongSingle(ifMatchHeader);

        ReportStatus status;
        try {
            status = ReportStatus.valueOf(request.status());
        } catch (IllegalArgumentException e) {
            throw new InvalidReportRequestException();
        }

        ReportStudyRef studyRef = new ReportStudyRef(studyInstanceUid);
        ReportActor actor = currentReportActor.actor();
        ReportContent content = new ReportContent(request.content());

        UpdateReportCommand command = new UpdateReportCommand(
            studyRef,
            actor,
            content,
            status,
            expectedVersion
        );

        StudyReportResult report = updateStudyReportUseCase.execute(command);

        LOG.infov("study report update completed: route={0}, status={1}, durationMs={2}",
            ROUTE_TEMPLATE, report.status(), elapsedMillis(started));

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
