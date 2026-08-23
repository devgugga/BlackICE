package dev.blackice.ingest.api;

import dev.blackice.ingest.application.input.UploadedDicom;
import dev.blackice.ingest.application.result.IngestResult;
import dev.blackice.ingest.application.usecase.IngestStudiesUseCase;
import dev.blackice.security.application.AccessTokenProvider;
import io.smallrye.common.annotation.Blocking;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** HTTP boundary for manual DICOM imports authenticated by the BFF session. */
@Path("/api/studies")
@RolesAllowed("auth")
public class IngestResource {

    private static final Logger LOG = Logger.getLogger(IngestResource.class);

    private final IngestStudiesUseCase useCase;
    private final AccessTokenProvider accessTokenProvider;
    private final int maxFiles;
    private final long maxTotalBytes;

    @Inject
    public IngestResource(
        IngestStudiesUseCase useCase,
        AccessTokenProvider accessTokenProvider,
        @ConfigProperty(name = "blackice.ingest.max-files", defaultValue = "500") int maxFiles,
        @ConfigProperty(name = "blackice.ingest.max-total-bytes", defaultValue = "524288000") long maxTotalBytes
    ) {
        this.useCase = Objects.requireNonNull(useCase, "useCase must not be null");
        this.accessTokenProvider = Objects.requireNonNull(accessTokenProvider, "accessTokenProvider must not be null");
        this.maxFiles = maxFiles;
        this.maxTotalBytes = maxTotalBytes;
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @Blocking
    public Response ingest(@RestForm("files") List<FileUpload> files) {
        if (files == null || files.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        if (files.size() > maxFiles) {
            return Response.status(Response.Status.REQUEST_ENTITY_TOO_LARGE).build();
        }
        long totalBytes = files.stream().mapToLong(FileUpload::size).sum();
        if (totalBytes > maxTotalBytes) {
            return Response.status(Response.Status.REQUEST_ENTITY_TOO_LARGE).build();
        }

        List<UploadedDicom> uploads = files.stream()
            .map(file -> new UploadedDicom(file.uploadedFile(), file.fileName(), file.size()))
            .toList();

        String requestId = UUID.randomUUID().toString();
        long started = System.nanoTime();
        MDC.put("requestId", requestId);
        try {
            LOG.infov("manual DICOM import started: files={0}, bytes={1}",
                uploads.size(), totalBytes);
            IngestResult result = useCase.ingest(uploads, accessTokenProvider.accessToken());
            LOG.infov("manual DICOM import finished: accepted={0}, rejected={1}, durationMs={2}",
                result.summary().archiveAccepted(),
                result.summary().archiveRejected(),
                Duration.ofNanos(System.nanoTime() - started).toMillis());
            return Response.status(IngestHttpStatusResolver.resolve(result))
                .header("X-Request-ID", requestId)
                .entity(result)
                .build();
        } finally {
            MDC.remove("requestId");
        }
    }
}
