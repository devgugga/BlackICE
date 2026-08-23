package dev.blackice.ingest.api;

import dev.blackice.ingest.application.input.UploadedDicom;
import dev.blackice.ingest.application.result.IngestResult;
import dev.blackice.ingest.application.usecase.IngestStudiesUseCase;
import dev.blackice.security.application.AccessTokenProvider;
import dev.blackice.shared.api.problem.ProblemResponseFactory;
import dev.blackice.shared.api.problem.generated.ProblemType;
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
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * HTTP boundary for manual DICOM imports authenticated by the BFF session.
 *
 * <p>The resource validates only the request limits and delegates: the use case
 * performs the ingest and {@link IngestResponseMapper} decides whether the
 * outcome is a result or a catalogued problem. Correlation comes from the W3C
 * trace context, so no identifier is created here.
 */
@Path("/api/studies")
@RolesAllowed("auth")
public class IngestResource {

    private static final Logger LOG = Logger.getLogger(IngestResource.class);

    private final IngestStudiesUseCase useCase;
    private final AccessTokenProvider accessTokenProvider;
    private final IngestResponseMapper responseMapper;
    private final ProblemResponseFactory problems;
    private final int maxFiles;
    private final long maxTotalBytes;

    @Inject
    public IngestResource(
        IngestStudiesUseCase useCase,
        AccessTokenProvider accessTokenProvider,
        IngestResponseMapper responseMapper,
        ProblemResponseFactory problems,
        @ConfigProperty(name = "blackice.ingest.max-files", defaultValue = "500") int maxFiles,
        @ConfigProperty(name = "blackice.ingest.max-total-bytes", defaultValue = "524288000") long maxTotalBytes
    ) {
        this.useCase = Objects.requireNonNull(useCase, "useCase must not be null");
        this.accessTokenProvider = Objects.requireNonNull(accessTokenProvider, "accessTokenProvider must not be null");
        this.responseMapper = Objects.requireNonNull(responseMapper, "responseMapper must not be null");
        this.problems = Objects.requireNonNull(problems, "problems must not be null");
        this.maxFiles = maxFiles;
        this.maxTotalBytes = maxTotalBytes;
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @Blocking
    public Response ingest(@RestForm("files") List<FileUpload> files) {
        if (files == null || files.isEmpty()) {
            return problems.response(ProblemType.API_UPLOAD_EMPTY);
        }
        if (files.size() > maxFiles) {
            return problems.response(ProblemType.API_PAYLOAD_TOO_LARGE);
        }
        long totalBytes = files.stream().mapToLong(FileUpload::size).sum();
        if (totalBytes > maxTotalBytes) {
            return problems.response(ProblemType.API_PAYLOAD_TOO_LARGE);
        }

        List<UploadedDicom> uploads = files.stream()
            .map(file -> new UploadedDicom(file.uploadedFile(), file.fileName(), file.size()))
            .toList();

        long started = System.nanoTime();
        LOG.infov("manual DICOM import started: files={0}, bytes={1}", uploads.size(), totalBytes);

        IngestResult result = useCase.ingest(uploads, accessTokenProvider.accessToken());

        LOG.infov("manual DICOM import finished: accepted={0}, rejected={1}, durationMs={2}",
            result.summary().archiveAccepted(),
            result.summary().archiveRejected(),
            Duration.ofNanos(System.nanoTime() - started).toMillis());

        return responseMapper.toResponse(result);
    }
}
