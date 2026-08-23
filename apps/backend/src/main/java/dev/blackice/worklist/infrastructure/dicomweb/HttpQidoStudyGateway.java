package dev.blackice.worklist.infrastructure.dicomweb;

import dev.blackice.worklist.application.exception.ArchiveSearchException;
import dev.blackice.worklist.application.input.StudySearchRequest;
import dev.blackice.worklist.application.port.StudyQueryGateway;
import dev.blackice.worklist.application.result.StudySummary;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

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
import java.util.List;
import java.util.Objects;

/**
 * HTTP DICOMweb adapter that queries study records using QIDO-RS.
 */
@ApplicationScoped
public class HttpQidoStudyGateway implements StudyQueryGateway {

    private static final String DICOM_JSON_MEDIA_TYPE = "application/dicom+json";

    private final String baseUrl;
    private final Duration requestTimeout;
    private final QidoQueryBuilder queryBuilder;
    private final QidoStudyResponseParser responseParser;
    private final HttpClient httpClient;
    private final W3cTraceContextInjector traceContextInjector = new W3cTraceContextInjector();

    @Inject
    public HttpQidoStudyGateway(
        @ConfigProperty(name = "blackice.dicomweb.base-url", defaultValue = "http://arc:8080/dcm4chee-arc/aets/DCM4CHEE/rs")
        String baseUrl,
        @ConfigProperty(name = "blackice.worklist.request-timeout", defaultValue = "10S")
        Duration requestTimeout,
        QidoQueryBuilder queryBuilder,
        QidoStudyResponseParser responseParser
    ) {
        this(
            baseUrl,
            requestTimeout,
            queryBuilder,
            responseParser,
            HttpClient.newBuilder().connectTimeout(requestTimeout).build()
        );
    }

    public HttpQidoStudyGateway(
        String baseUrl,
        Duration requestTimeout,
        QidoQueryBuilder queryBuilder,
        QidoStudyResponseParser responseParser,
        HttpClient httpClient
    ) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout must not be null");
        this.queryBuilder = Objects.requireNonNull(queryBuilder, "queryBuilder must not be null");
        this.responseParser = Objects.requireNonNull(responseParser, "responseParser must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
    }

    @Override
    public List<StudySummary> search(StudySearchRequest request, int fetchLimit, String accessToken) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(accessToken, "accessToken must not be null");

        URI uri = queryBuilder.build(baseUrl, request, fetchLimit);

        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
            .timeout(requestTimeout)
            .header("Authorization", "Bearer " + accessToken)
            .header("Accept", DICOM_JSON_MEDIA_TYPE)
            .GET();
        traceContextInjector.inject(builder);
        HttpRequest httpRequest = builder.build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException e) {
            throw new ArchiveSearchException(ArchiveSearchException.Reason.TIMEOUT, e);
        } catch (ConnectException e) {
            throw new ArchiveSearchException(ArchiveSearchException.Reason.CONNECTION, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ArchiveSearchException(ArchiveSearchException.Reason.CONNECTION, e);
        } catch (IOException e) {
            if (e.getCause() instanceof HttpTimeoutException || e instanceof HttpTimeoutException) {
                throw new ArchiveSearchException(ArchiveSearchException.Reason.TIMEOUT, e);
            }
            throw new ArchiveSearchException(ArchiveSearchException.Reason.CONNECTION, e);
        }
        // Sem catch genérico: um bug inesperado sobe para o fallback 500 da
        // fronteira em vez de virar indisponibilidade do Archive.

        int statusCode = response.statusCode();
        if (statusCode == 413) {
            throw new ArchiveSearchException(ArchiveSearchException.Reason.QUERY_TOO_BROAD, null);
        }
        if (statusCode < 200 || statusCode >= 300) {
            throw new ArchiveSearchException(ArchiveSearchException.Reason.HTTP_STATUS, null);
        }

        if (statusCode == 204) {
            return List.of();
        }

        String contentType = response.headers().firstValue("Content-Type").orElse("");
        String mediaType = contentType.split(";")[0].trim().toLowerCase();
        if (!DICOM_JSON_MEDIA_TYPE.equals(mediaType)) {
            throw new ArchiveSearchException(ArchiveSearchException.Reason.INVALID_RESPONSE, null);
        }

        try {
            return responseParser.parse(response.body());
        } catch (Exception e) {
            throw new ArchiveSearchException(ArchiveSearchException.Reason.INVALID_RESPONSE, e);
        }
    }
}
