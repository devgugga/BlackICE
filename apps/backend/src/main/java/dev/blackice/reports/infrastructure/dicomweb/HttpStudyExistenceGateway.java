package dev.blackice.reports.infrastructure.dicomweb;

import dev.blackice.reports.application.exception.ArchiveStudyLookupException;
import dev.blackice.reports.application.input.ReportStudyRef;
import dev.blackice.reports.application.port.StudyExistenceGateway;
import dev.blackice.shared.infrastructure.telemetry.W3cTraceContextInjector;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/**
 * HTTP DICOMweb adapter that verifies study existence using QIDO-RS.
 */
@ApplicationScoped
public class HttpStudyExistenceGateway implements StudyExistenceGateway {

    private static final String DICOM_JSON_MEDIA_TYPE = "application/dicom+json";

    private final String baseUrl;
    private final Duration requestTimeout;
    private final ReportQidoQueryBuilder queryBuilder;
    private final ReportQidoResponseParser responseParser;
    private final HttpClient httpClient;
    private final W3cTraceContextInjector traceContextInjector = new W3cTraceContextInjector();

    @Inject
    public HttpStudyExistenceGateway(
        @ConfigProperty(name = "blackice.dicomweb.base-url", defaultValue = "http://arc:8080/dcm4chee-arc/aets/DCM4CHEE/rs")
        String baseUrl,
        @ConfigProperty(name = "blackice.reports.archive-request-timeout", defaultValue = "10S")
        Duration requestTimeout,
        ReportQidoQueryBuilder queryBuilder,
        ReportQidoResponseParser responseParser
    ) {
        this(
            baseUrl,
            requestTimeout,
            queryBuilder,
            responseParser,
            HttpClient.newBuilder().connectTimeout(requestTimeout).build()
        );
    }

    public HttpStudyExistenceGateway(
        String baseUrl,
        Duration requestTimeout,
        ReportQidoQueryBuilder queryBuilder,
        ReportQidoResponseParser responseParser,
        HttpClient httpClient
    ) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout must not be null");
        this.queryBuilder = Objects.requireNonNull(queryBuilder, "queryBuilder must not be null");
        this.responseParser = Objects.requireNonNull(responseParser, "responseParser must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
    }

    @Override
    public boolean exists(ReportStudyRef study, String accessToken) {
        Objects.requireNonNull(study, "study must not be null");
        Objects.requireNonNull(accessToken, "accessToken must not be null");

        URI uri = queryBuilder.build(baseUrl, study);

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
            throw new ArchiveStudyLookupException(ArchiveStudyLookupException.Reason.ARCHIVE_UNAVAILABLE, e);
        } catch (ConnectException e) {
            throw new ArchiveStudyLookupException(ArchiveStudyLookupException.Reason.ARCHIVE_UNAVAILABLE, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ArchiveStudyLookupException(ArchiveStudyLookupException.Reason.ARCHIVE_UNAVAILABLE, e);
        } catch (IOException e) {
            if (e.getCause() instanceof HttpTimeoutException || e instanceof HttpTimeoutException) {
                throw new ArchiveStudyLookupException(ArchiveStudyLookupException.Reason.ARCHIVE_UNAVAILABLE, e);
            }
            throw new ArchiveStudyLookupException(ArchiveStudyLookupException.Reason.ARCHIVE_UNAVAILABLE, e);
        }
        // No generic catch: unexpected implementation bugs must reach the 500 fallback unwrapped.

        int statusCode = response.statusCode();

        if (statusCode == 204 || statusCode == 404) {
            return false;
        }

        if (statusCode == 401 || statusCode == 403) {
            throw new ArchiveStudyLookupException(ArchiveStudyLookupException.Reason.ARCHIVE_AUTH_FAILED);
        }

        if (statusCode >= 500) {
            throw new ArchiveStudyLookupException(ArchiveStudyLookupException.Reason.ARCHIVE_UNAVAILABLE);
        }

        if (statusCode != 200) {
            throw new ArchiveStudyLookupException(ArchiveStudyLookupException.Reason.ARCHIVE_INVALID_RESPONSE);
        }

        String contentType = response.headers().firstValue("Content-Type").orElse("");
        String mediaType = contentType.split(";")[0].trim().toLowerCase();
        if (!DICOM_JSON_MEDIA_TYPE.equals(mediaType)) {
            throw new ArchiveStudyLookupException(ArchiveStudyLookupException.Reason.ARCHIVE_INVALID_RESPONSE);
        }

        return responseParser.parse(response.body(), study);
    }
}
