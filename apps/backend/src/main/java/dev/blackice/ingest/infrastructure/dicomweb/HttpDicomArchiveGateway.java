package dev.blackice.ingest.infrastructure.dicomweb;

import dev.blackice.ingest.application.exception.ArchiveUnavailableException;
import dev.blackice.ingest.application.result.StowStudyResult;
import dev.blackice.ingest.application.validation.ValidatedDicom;
import dev.blackice.ingest.application.port.DicomArchiveGateway;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import dev.blackice.shared.infrastructure.telemetry.W3cTraceContextInjector;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** HTTP DICOMweb adapter that sends validated studies with STOW-RS. */
@ApplicationScoped
public class HttpDicomArchiveGateway implements DicomArchiveGateway {

    private final String baseUrl;
    private final Duration requestTimeout;
    private final StowResponseParser parser;
    private final HttpClient httpClient;
    private final W3cTraceContextInjector traceContextInjector = new W3cTraceContextInjector();

    @Inject
    public HttpDicomArchiveGateway(
        @ConfigProperty(name = "blackice.dicomweb.base-url", defaultValue = "http://arc:8080/dcm4chee-arc/aets/DCM4CHEE/rs")
        String baseUrl,
        @ConfigProperty(name = "blackice.dicomweb.request-timeout", defaultValue = "PT30S")
        Duration requestTimeout,
        StowResponseParser parser
    ) {
        this(
            baseUrl,
            requestTimeout,
            parser,
            HttpClient.newBuilder().connectTimeout(requestTimeout).build()
        );
    }

    public HttpDicomArchiveGateway(
        String baseUrl,
        Duration requestTimeout,
        StowResponseParser parser,
        HttpClient httpClient
    ) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout must not be null");
        this.parser = Objects.requireNonNull(parser, "parser must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
    }

    @Override
    public StowStudyResult storeStudy(
        String studyInstanceUid,
        List<ValidatedDicom> files,
        String accessToken
    ) {
        Objects.requireNonNull(studyInstanceUid, "studyInstanceUid must not be null");
        Objects.requireNonNull(files, "files must not be null");
        Objects.requireNonNull(accessToken, "accessToken must not be null");

        String normalizedBaseUrl = baseUrl.replaceAll("/+$", "");
        URI uri = URI.create(normalizedBaseUrl + "/studies/" + studyInstanceUid);
        String boundary = "blackice-boundary-" + UUID.randomUUID();

        HttpRequest.BodyPublisher bodyPublisher;
        try {
            bodyPublisher = MultipartRelatedBodyPublisher.publish(files, boundary);
        } catch (FileNotFoundException e) {
            throw new ArchiveUnavailableException(ArchiveUnavailableException.Reason.CONNECTION, e);
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
            .timeout(requestTimeout)
            .header("Authorization", "Bearer " + accessToken)
            .header("Accept", "application/dicom+json")
            .header("Content-Type", "multipart/related; type=\"application/dicom\"; boundary=" + boundary)
            .POST(bodyPublisher);
        traceContextInjector.inject(builder);
        HttpRequest request = builder.build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpConnectTimeoutException e) {
            throw new ArchiveUnavailableException(ArchiveUnavailableException.Reason.TIMEOUT, e);
        } catch (HttpTimeoutException e) {
            throw new ArchiveUnavailableException(ArchiveUnavailableException.Reason.TIMEOUT, e);
        } catch (ConnectException e) {
            throw new ArchiveUnavailableException(ArchiveUnavailableException.Reason.CONNECTION, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ArchiveUnavailableException(ArchiveUnavailableException.Reason.INTERRUPTED, e);
        } catch (IOException e) {
            if (e.getCause() instanceof HttpTimeoutException || e instanceof HttpTimeoutException) {
                throw new ArchiveUnavailableException(ArchiveUnavailableException.Reason.TIMEOUT, e);
            }
            throw new ArchiveUnavailableException(ArchiveUnavailableException.Reason.CONNECTION, e);
        }
        // Sem catch genérico: um bug inesperado sobe para o fallback 500 da
        // fronteira em vez de virar indisponibilidade do Archive.

        int statusCode = response.statusCode();
        if (statusCode < 200 || statusCode >= 300) {
            throw new ArchiveUnavailableException(ArchiveUnavailableException.Reason.HTTP_STATUS, null);
        }

        Set<String> submittedSopUids = files.stream()
            .map(ValidatedDicom::sopInstanceUid)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        try {
            return parser.parse(studyInstanceUid, response.body(), submittedSopUids);
        } catch (Exception e) {
            // O archive respondeu 2xx: as instâncias podem já ter sido gravadas.
            // Isso não é indisponibilidade, e tratá-lo como tal induziria reenvio.
            throw new ArchiveUnavailableException(ArchiveUnavailableException.Reason.INVALID_RESPONSE, e);
        }
    }
}
