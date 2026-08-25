package dev.blackice.viewer.infrastructure.dicomweb;

import dev.blackice.shared.infrastructure.telemetry.W3cTraceContextInjector;
import dev.blackice.viewer.application.exception.ArchiveViewerException;
import dev.blackice.viewer.application.input.ViewerInstanceRef;
import dev.blackice.viewer.application.port.DicomFrameGateway;
import dev.blackice.viewer.application.result.FrameStream;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

/**
 * HTTP DICOMweb adapter for streaming raw uncompressed/compressed pixel data frames using WADO-RS Retrieve Rendered/Frames.
 *
 * <p>Negotiates exact multipart/related binary streams from DCM4CHEE without buffering
 * or transcoding the underlying pixel body.</p>
 */
@ApplicationScoped
public class HttpDicomFrameGateway implements DicomFrameGateway {

    private static final String ACCEPT_MULTIPART_OCTET_STREAM =
        "multipart/related; type=\"application/octet-stream\"; transfer-syntax=*";

    private final String baseUrl;
    private final Duration requestTimeout;
    private final HttpClient httpClient;
    private final W3cTraceContextInjector traceContextInjector = new W3cTraceContextInjector();

    @Inject
    public HttpDicomFrameGateway(
        @ConfigProperty(name = "blackice.dicomweb.base-url", defaultValue = "http://arc:8080/dcm4chee-arc/aets/DCM4CHEE/rs")
        String baseUrl,
        @ConfigProperty(name = "blackice.dicomweb.request-timeout", defaultValue = "60S")
        Duration requestTimeout
    ) {
        this(
            baseUrl,
            requestTimeout,
            HttpClient.newBuilder().connectTimeout(requestTimeout).build()
        );
    }

    public HttpDicomFrameGateway(
        String baseUrl,
        Duration requestTimeout,
        HttpClient httpClient
    ) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
    }

    @Override
    public FrameStream retrieveFirstFrame(ViewerInstanceRef instance, String accessToken) {
        Objects.requireNonNull(instance, "instance must not be null");
        Objects.requireNonNull(accessToken, "accessToken must not be null");

        URI uri = buildFrameUri(instance);
        HttpResponse<InputStream> response = executeGet(uri, accessToken);

        int statusCode = response.statusCode();
        if (statusCode == 401) {
            closeQuietly(response.body());
            throw new ArchiveViewerException(ArchiveViewerException.Reason.AUTHENTICATION);
        }
        if (statusCode == 403) {
            closeQuietly(response.body());
            throw new ArchiveViewerException(ArchiveViewerException.Reason.ACCESS_DENIED);
        }
        if (statusCode == 404) {
            closeQuietly(response.body());
            throw new ArchiveViewerException(ArchiveViewerException.Reason.NOT_FOUND);
        }
        if (statusCode >= 500) {
            closeQuietly(response.body());
            throw new ArchiveViewerException(ArchiveViewerException.Reason.UNAVAILABLE);
        }
        if (statusCode < 200 || statusCode >= 300) {
            closeQuietly(response.body());
            throw new ArchiveViewerException(ArchiveViewerException.Reason.INVALID_RESPONSE);
        }

        validateMultipartContentType(response);

        String contentType = response.headers().firstValue("Content-Type").orElseThrow();
        return new FrameStream(contentType, response.body());
    }

    private URI buildFrameUri(ViewerInstanceRef instance) {
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return URI.create(normalizedBase + "/studies/" + instance.studyInstanceUid()
            + "/series/" + instance.seriesInstanceUid()
            + "/instances/" + instance.sopInstanceUid()
            + "/frames/1");
    }

    private HttpResponse<InputStream> executeGet(URI uri, String accessToken) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
            .timeout(requestTimeout)
            .header("Authorization", "Bearer " + accessToken)
            .header("Accept", ACCEPT_MULTIPART_OCTET_STREAM)
            .GET();
        traceContextInjector.inject(builder);
        HttpRequest httpRequest = builder.build();

        try {
            return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
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
    }

    private void validateMultipartContentType(HttpResponse<InputStream> response) {
        String contentType = response.headers().firstValue("Content-Type").orElse(null);
        if (!isValidMultipartContentType(contentType)) {
            closeQuietly(response.body());
            throw new ArchiveViewerException(ArchiveViewerException.Reason.INVALID_RESPONSE);
        }
    }

    static boolean isValidMultipartContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        String lower = contentType.toLowerCase(Locale.ROOT);
        if (!lower.contains("multipart/related")) {
            return false;
        }
        boolean hasOctetStreamType = lower.contains("type=\"application/octet-stream\"")
            || lower.contains("type=application/octet-stream");
        if (!hasOctetStreamType) {
            return false;
        }
        int boundaryIdx = lower.indexOf("boundary=");
        if (boundaryIdx == -1) {
            return false;
        }
        String boundaryPart = contentType.substring(boundaryIdx + 9).trim();
        if (boundaryPart.isEmpty()) {
            return false;
        }
        if (boundaryPart.startsWith("\"")) {
            int closingQuote = boundaryPart.indexOf('"', 1);
            if (closingQuote <= 1) {
                return false;
            }
        } else {
            int semi = boundaryPart.indexOf(';');
            String val = (semi != -1 ? boundaryPart.substring(0, semi) : boundaryPart).trim();
            if (val.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static void closeQuietly(InputStream body) {
        if (body != null) {
            try {
                body.close();
            } catch (IOException ignored) {
                // Ignore failure on stream cleanup
            }
        }
    }
}
