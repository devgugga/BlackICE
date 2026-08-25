package dev.blackice.viewer.api;

import dev.blackice.security.application.AccessTokenProvider;
import dev.blackice.viewer.application.input.ViewerInstanceRef;
import dev.blackice.viewer.application.result.FrameStream;
import dev.blackice.viewer.application.usecase.RetrieveFrameUseCase;
import io.smallrye.common.annotation.Blocking;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.Objects;

/**
 * HTTP boundary for streaming raw DICOMweb multipart frame pixel data.
 *
 * <p>Serves as the narrow WADO-RS frame proxy at {@code /api/dicomweb/studies/{studyUid}/series/{seriesUid}/instances/{sopUid}/frames/1}.
 * Streams upstream binary frames directly without re-encoding, transcoding, or buffering the pixel payload.
 * Protected by the BFF session.</p>
 */
@Path("/api/dicomweb/studies/{studyUid}/series/{seriesUid}/instances/{sopUid}/frames/1")
@RolesAllowed("auth")
public class WadoFrameResource {

    private static final Logger LOG = Logger.getLogger(WadoFrameResource.class);
    private static final String ROUTE_TEMPLATE =
        "/api/dicomweb/studies/{studyUid}/series/{seriesUid}/instances/{sopUid}/frames/1";

    private final RetrieveFrameUseCase retrieveFrameUseCase;
    private final AccessTokenProvider accessTokenProvider;

    @Inject
    public WadoFrameResource(
        RetrieveFrameUseCase retrieveFrameUseCase,
        AccessTokenProvider accessTokenProvider
    ) {
        this.retrieveFrameUseCase = Objects.requireNonNull(retrieveFrameUseCase, "retrieveFrameUseCase must not be null");
        this.accessTokenProvider = Objects.requireNonNull(accessTokenProvider, "accessTokenProvider must not be null");
    }

    @GET
    @Blocking
    public Response frame(
        @PathParam("studyUid") String studyUid,
        @PathParam("seriesUid") String seriesUid,
        @PathParam("sopUid") String sopUid
    ) {
        long started = System.nanoTime();
        ViewerInstanceRef instanceRef = new ViewerInstanceRef(studyUid, seriesUid, sopUid);
        FrameStream stream = retrieveFrameUseCase.execute(instanceRef, accessTokenProvider.accessToken());

        LOG.infov("viewer frame stream opened: route={0}, durationMs={1}",
            ROUTE_TEMPLATE, elapsedMillis(started));

        StreamingOutput output = destination -> {
            try (FrameStream frame = stream) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = frame.body().read(buffer)) != -1) {
                    destination.write(buffer, 0, bytesRead);
                    destination.flush();
                }
            }
        };

        return Response.ok(output)
            .type(stream.contentType())
            .header("Cache-Control", "private, no-store")
            .build();
    }

    private static long elapsedMillis(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
    }
}
