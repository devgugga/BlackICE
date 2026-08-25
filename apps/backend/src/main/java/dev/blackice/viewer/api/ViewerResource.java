package dev.blackice.viewer.api;

import dev.blackice.security.application.AccessTokenProvider;
import dev.blackice.viewer.application.input.ViewerSeriesRef;
import dev.blackice.viewer.application.input.ViewerStudyRef;
import dev.blackice.viewer.application.result.StudyViewerSummary;
import dev.blackice.viewer.application.result.ViewerSeriesInstances;
import dev.blackice.viewer.application.usecase.GetSeriesInstancesUseCase;
import dev.blackice.viewer.application.usecase.GetStudyViewerUseCase;
import io.smallrye.common.annotation.Blocking;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.Objects;

/**
 * HTTP boundary for curated study viewer exploration and instance metadata retrieval.
 *
 * <p>Protected by the BFF session. Failures are mapped by {@link ViewerExceptionMappers} and
 * unexpected exceptions fall through to the shared boundary. Logs only route templates,
 * durations, and non-clinical counts without echoing clinical UIDs.</p>
 */
@Path("/api/studies")
@RolesAllowed("auth")
public class ViewerResource {

    private static final Logger LOG = Logger.getLogger(ViewerResource.class);
    private static final String STUDY_ROUTE_TEMPLATE = "/api/studies/{studyUid}";
    private static final String INSTANCES_ROUTE_TEMPLATE = "/api/studies/{studyUid}/series/{seriesUid}/instances";

    private final GetStudyViewerUseCase getStudyViewerUseCase;
    private final GetSeriesInstancesUseCase getSeriesInstancesUseCase;
    private final AccessTokenProvider accessTokenProvider;

    @Inject
    public ViewerResource(
        GetStudyViewerUseCase getStudyViewerUseCase,
        GetSeriesInstancesUseCase getSeriesInstancesUseCase,
        AccessTokenProvider accessTokenProvider
    ) {
        this.getStudyViewerUseCase = Objects.requireNonNull(getStudyViewerUseCase, "getStudyViewerUseCase must not be null");
        this.getSeriesInstancesUseCase = Objects.requireNonNull(getSeriesInstancesUseCase, "getSeriesInstancesUseCase must not be null");
        this.accessTokenProvider = Objects.requireNonNull(accessTokenProvider, "accessTokenProvider must not be null");
    }

    @GET
    @Path("/{studyUid}")
    @Produces(MediaType.APPLICATION_JSON)
    @Blocking
    public Response study(@PathParam("studyUid") String studyUid) {
        long started = System.nanoTime();
        ViewerStudyRef studyRef = new ViewerStudyRef(studyUid);
        StudyViewerSummary summary = getStudyViewerUseCase.execute(studyRef, accessTokenProvider.accessToken());

        LOG.infov("viewer study retrieved: route={0}, series={1}, durationMs={2}",
            STUDY_ROUTE_TEMPLATE, summary.series().size(), elapsedMillis(started));
        return Response.ok(summary).build();
    }

    @GET
    @Path("/{studyUid}/series/{seriesUid}/instances")
    @Produces(MediaType.APPLICATION_JSON)
    @Blocking
    public Response instances(
        @PathParam("studyUid") String studyUid,
        @PathParam("seriesUid") String seriesUid
    ) {
        long started = System.nanoTime();
        ViewerSeriesRef seriesRef = new ViewerSeriesRef(studyUid, seriesUid);
        ViewerSeriesInstances instances = getSeriesInstancesUseCase.execute(seriesRef, accessTokenProvider.accessToken());

        LOG.infov("viewer series instances retrieved: route={0}, instances={1}, durationMs={2}",
            INSTANCES_ROUTE_TEMPLATE, instances.instances().size(), elapsedMillis(started));
        return Response.ok(instances).build();
    }

    private static long elapsedMillis(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
    }
}
