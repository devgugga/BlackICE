package dev.blackice.viewer.infrastructure.dicomweb;

import dev.blackice.shared.infrastructure.telemetry.W3cTraceContextInjector;
import dev.blackice.viewer.application.exception.ArchiveViewerException;
import dev.blackice.viewer.application.exception.InvalidArchiveMetadataException;
import dev.blackice.viewer.application.input.ViewerSeriesRef;
import dev.blackice.viewer.application.port.SeriesMetadataGateway;
import dev.blackice.viewer.application.result.ViewerInstance;
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
import java.util.List;
import java.util.Objects;

/**
 * HTTP DICOMweb adapter that retrieves active series metadata using WADO-RS Retrieve Series Metadata.
 */
@ApplicationScoped
public class HttpSeriesMetadataGateway implements SeriesMetadataGateway {

    private static final String DICOM_JSON_MEDIA_TYPE = "application/dicom+json";

    private final String baseUrl;
    private final Duration requestTimeout;
    private final WadoSeriesMetadataParser responseParser;
    private final HttpClient httpClient;
    private final W3cTraceContextInjector traceContextInjector = new W3cTraceContextInjector();

    @Inject
    public HttpSeriesMetadataGateway(
        @ConfigProperty(name = "blackice.dicomweb.base-url", defaultValue = "http://arc:8080/dcm4chee-arc/aets/DCM4CHEE/rs")
        String baseUrl,
        @ConfigProperty(name = "blackice.dicomweb.request-timeout", defaultValue = "60S")
        Duration requestTimeout,
        WadoSeriesMetadataParser responseParser
    ) {
        this(
            baseUrl,
            requestTimeout,
            responseParser,
            HttpClient.newBuilder().connectTimeout(requestTimeout).build()
        );
    }

    public HttpSeriesMetadataGateway(
        String baseUrl,
        Duration requestTimeout,
        WadoSeriesMetadataParser responseParser,
        HttpClient httpClient
    ) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout must not be null");
        this.responseParser = Objects.requireNonNull(responseParser, "responseParser must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
    }

    @Override
    public List<ViewerInstance> retrieve(ViewerSeriesRef series, String accessToken) {
        Objects.requireNonNull(series, "series must not be null");
        Objects.requireNonNull(accessToken, "accessToken must not be null");

        URI uri = buildMetadataUri(series);
        HttpResponse<String> response = executeGet(uri, accessToken);

        int statusCode = response.statusCode();
        if (statusCode == 204) {
            return List.of();
        }

        validateDicomJsonContentType(response);

        try {
            return responseParser.parse(response.body(), series);
        } catch (InvalidArchiveMetadataException e) {
            throw new ArchiveViewerException(ArchiveViewerException.Reason.INVALID_RESPONSE, e);
        }
    }

    private URI buildMetadataUri(ViewerSeriesRef series) {
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return URI.create(normalizedBase + "/studies/" + series.studyInstanceUid() + "/series/" + series.seriesInstanceUid() + "/metadata");
    }

    private HttpResponse<String> executeGet(URI uri, String accessToken) {
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
            throw new ArchiveViewerException(ArchiveViewerException.Reason.TIMEOUT, e);
        } catch (ConnectException e) {
            throw new ArchiveViewerException(ArchiveViewerException.Reason.CONNECTION, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ArchiveViewerException(ArchiveViewerException.Reason.CONNECTION, e);
        } catch (IOException e) {
            if (e.getCause() instanceof HttpTimeoutException || e instanceof HttpTimeoutException) {
                throw new ArchiveViewerException(ArchiveViewerException.Reason.TIMEOUT, e);
            }
            throw new ArchiveViewerException(ArchiveViewerException.Reason.CONNECTION, e);
        }

        int statusCode = response.statusCode();
        if (statusCode == 401) {
            throw new ArchiveViewerException(ArchiveViewerException.Reason.AUTHENTICATION);
        }
        if (statusCode == 403) {
            throw new ArchiveViewerException(ArchiveViewerException.Reason.ACCESS_DENIED);
        }
        if (statusCode == 404) {
            throw new ArchiveViewerException(ArchiveViewerException.Reason.NOT_FOUND);
        }
        if (statusCode >= 500) {
            throw new ArchiveViewerException(ArchiveViewerException.Reason.UNAVAILABLE);
        }
        if (statusCode < 200 || statusCode >= 300) {
            throw new ArchiveViewerException(ArchiveViewerException.Reason.INVALID_RESPONSE);
        }

        return response;
    }

    private void validateDicomJsonContentType(HttpResponse<String> response) {
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        String mediaType = contentType.split(";")[0].trim().toLowerCase();
        if (!DICOM_JSON_MEDIA_TYPE.equals(mediaType)) {
            throw new ArchiveViewerException(ArchiveViewerException.Reason.INVALID_RESPONSE);
        }
    }
}
