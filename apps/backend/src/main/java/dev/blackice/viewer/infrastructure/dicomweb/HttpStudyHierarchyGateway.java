package dev.blackice.viewer.infrastructure.dicomweb;

import dev.blackice.shared.infrastructure.telemetry.W3cTraceContextInjector;
import dev.blackice.viewer.application.exception.ArchiveViewerException;
import dev.blackice.viewer.application.input.ViewerStudyRef;
import dev.blackice.viewer.application.port.StudyHierarchyGateway;
import dev.blackice.viewer.application.result.InstanceIdentityMetadata;
import dev.blackice.viewer.application.result.SeriesMetadata;
import dev.blackice.viewer.application.result.StudyMetadata;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * HTTP DICOMweb adapter that queries study hierarchy and paginated instance identities using QIDO-RS.
 */
@ApplicationScoped
public class HttpStudyHierarchyGateway implements StudyHierarchyGateway {

    private static final String DICOM_JSON_MEDIA_TYPE = "application/dicom+json";
    private static final Pattern WARNING_299 = Pattern.compile("^\\s*299(?:\\s|$)");

    private final String baseUrl;
    private final Duration requestTimeout;
    private final int pageSize;
    private final QidoViewerQueryBuilder queryBuilder;
    private final QidoViewerResponseParser responseParser;
    private final HttpClient httpClient;
    private final W3cTraceContextInjector traceContextInjector = new W3cTraceContextInjector();

    @Inject
    public HttpStudyHierarchyGateway(
        @ConfigProperty(name = "blackice.dicomweb.base-url", defaultValue = "http://arc:8080/dcm4chee-arc/aets/DCM4CHEE/rs")
        String baseUrl,
        @ConfigProperty(name = "blackice.viewer.qido-request-timeout", defaultValue = "10S")
        Duration requestTimeout,
        @ConfigProperty(name = "blackice.viewer.classification-page-size", defaultValue = "500")
        int pageSize,
        QidoViewerQueryBuilder queryBuilder,
        QidoViewerResponseParser responseParser
    ) {
        this(
            baseUrl,
            requestTimeout,
            pageSize,
            queryBuilder,
            responseParser,
            HttpClient.newBuilder().connectTimeout(requestTimeout).build()
        );
    }

    public HttpStudyHierarchyGateway(
        String baseUrl,
        Duration requestTimeout,
        int pageSize,
        QidoViewerQueryBuilder queryBuilder,
        QidoViewerResponseParser responseParser,
        HttpClient httpClient
    ) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout must not be null");
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be greater than 0");
        }
        this.pageSize = pageSize;
        this.queryBuilder = Objects.requireNonNull(queryBuilder, "queryBuilder must not be null");
        this.responseParser = Objects.requireNonNull(responseParser, "responseParser must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
    }

    @Override
    public StudyMetadata findStudy(ViewerStudyRef study, String accessToken) {
        Objects.requireNonNull(study, "study must not be null");
        Objects.requireNonNull(accessToken, "accessToken must not be null");

        URI uri = queryBuilder.study(baseUrl, study);
        HttpResponse<String> response = executeGet(uri, accessToken);

        if (hasMoreResults(response)) {
            throw new ArchiveViewerException(ArchiveViewerException.Reason.INVALID_RESPONSE);
        }

        int statusCode = response.statusCode();
        if (statusCode == 204) {
            throw new ArchiveViewerException(ArchiveViewerException.Reason.NOT_FOUND);
        }

        validateDicomJsonContentType(response);

        StudyMetadata result;
        try {
            result = responseParser.parseStudy(response.body());
        } catch (QidoViewerResponseParser.InvalidResponseException e) {
            throw new ArchiveViewerException(ArchiveViewerException.Reason.INVALID_RESPONSE, e);
        }

        if (result == null) {
            throw new ArchiveViewerException(ArchiveViewerException.Reason.NOT_FOUND);
        }
        if (!study.studyInstanceUid().equals(result.studyInstanceUid())) {
            throw new ArchiveViewerException(ArchiveViewerException.Reason.INVALID_RESPONSE);
        }

        return result;
    }

    @Override
    public List<SeriesMetadata> findSeries(ViewerStudyRef study, String accessToken) {
        Objects.requireNonNull(study, "study must not be null");
        Objects.requireNonNull(accessToken, "accessToken must not be null");

        URI uri = queryBuilder.series(baseUrl, study);
        HttpResponse<String> response = executeGet(uri, accessToken);

        if (hasMoreResults(response)) {
            throw new ArchiveViewerException(ArchiveViewerException.Reason.INVALID_RESPONSE);
        }

        int statusCode = response.statusCode();
        if (statusCode == 204) {
            return List.of();
        }

        validateDicomJsonContentType(response);

        try {
            return responseParser.parseSeries(response.body());
        } catch (QidoViewerResponseParser.InvalidResponseException e) {
            throw new ArchiveViewerException(ArchiveViewerException.Reason.INVALID_RESPONSE, e);
        }
    }

    @Override
    public List<InstanceIdentityMetadata> findInstances(ViewerStudyRef study, String accessToken) {
        Objects.requireNonNull(study, "study must not be null");
        Objects.requireNonNull(accessToken, "accessToken must not be null");

        List<InstanceIdentityMetadata> allInstances = new ArrayList<>();
        Set<String> seenSopUids = new HashSet<>();
        int offset = 0;

        while (true) {
            URI uri = queryBuilder.instances(baseUrl, study, pageSize, offset);
            HttpResponse<String> response = executeGet(uri, accessToken);
            boolean moreResults = hasMoreResults(response);

            List<InstanceIdentityMetadata> pageInstances;
            int statusCode = response.statusCode();
            if (statusCode == 204) {
                pageInstances = List.of();
            } else {
                validateDicomJsonContentType(response);
                try {
                    pageInstances = responseParser.parseInstances(response.body());
                } catch (QidoViewerResponseParser.InvalidResponseException e) {
                    throw new ArchiveViewerException(ArchiveViewerException.Reason.INVALID_RESPONSE, e);
                }
            }

            if (pageInstances.isEmpty() && moreResults) {
                throw new ArchiveViewerException(ArchiveViewerException.Reason.INVALID_RESPONSE);
            }

            for (InstanceIdentityMetadata instance : pageInstances) {
                if (!seenSopUids.add(instance.sopInstanceUid())) {
                    throw new ArchiveViewerException(ArchiveViewerException.Reason.INVALID_RESPONSE);
                }
                allInstances.add(instance);
            }

            if (!moreResults) {
                break;
            }
            offset += pageInstances.size();
        }

        return List.copyOf(allInstances);
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

    private static boolean hasMoreResults(HttpResponse<?> response) {
        return response.headers().allValues("Warning").stream()
            .anyMatch(value -> WARNING_299.matcher(value).find());
    }
}
