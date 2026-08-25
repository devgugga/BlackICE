package dev.blackice.viewer.infrastructure.dicomweb;

import dev.blackice.viewer.application.input.ViewerStudyRef;
import jakarta.enterprise.context.ApplicationScoped;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Builds deterministic QIDO-RS query URIs for study metadata, series discovery, and paginated instance identities.
 */
@ApplicationScoped
public class QidoViewerQueryBuilder {

    private static final String STUDIES_SEGMENT = "/studies";
    private static final String STUDY_INCLUDE_FIELDS = "00100021,00080030,00081030";
    private static final String SERIES_INCLUDE_FIELDS = "0008103E,00201209";
    private static final String INSTANCES_INCLUDE_FIELDS = "0020000E,00080016,00080018,00280008";
    private static final String INSTANCES_ORDER_BY = "SeriesInstanceUID,SOPInstanceUID";

    /**
     * Builds the QIDO-RS URI to query metadata for a single study.
     *
     * @param baseUrl base DICOMweb URL of the archive
     * @param ref validated study reference
     * @return target QIDO URI
     */
    public URI study(String baseUrl, ViewerStudyRef ref) {
        validateCommon(baseUrl, ref);

        String rootUrl = normalizeStudiesRoot(baseUrl);
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("StudyInstanceUID", ref.studyInstanceUid());
        parameters.put("limit", "2");
        parameters.put("includefield", STUDY_INCLUDE_FIELDS);

        return URI.create(rootUrl + "?" + buildQueryString(parameters));
    }

    /**
     * Builds the QIDO-RS URI to discover all series belonging to a study.
     *
     * @param baseUrl base DICOMweb URL of the archive
     * @param ref validated study reference
     * @return target QIDO URI
     */
    public URI series(String baseUrl, ViewerStudyRef ref) {
        validateCommon(baseUrl, ref);

        String rootUrl = normalizeStudiesRoot(baseUrl);
        String endpointUrl = rootUrl + "/" + encodeRfc3986(ref.studyInstanceUid()) + "/series";
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("includefield", SERIES_INCLUDE_FIELDS);

        return URI.create(endpointUrl + "?" + buildQueryString(parameters));
    }

    /**
     * Builds the QIDO-RS URI to query a page of instance identities for a study.
     *
     * @param baseUrl base DICOMweb URL of the archive
     * @param ref validated study reference
     * @param limit maximum instances to return in the page (e.g. 500)
     * @param offset zero-based pagination offset
     * @return target QIDO URI
     */
    public URI instances(String baseUrl, ViewerStudyRef ref, int limit, int offset) {
        validateCommon(baseUrl, ref);
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be greater than 0");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be non-negative");
        }

        String rootUrl = normalizeStudiesRoot(baseUrl);
        String endpointUrl = rootUrl + "/" + encodeRfc3986(ref.studyInstanceUid()) + "/instances";
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("limit", Integer.toString(limit));
        parameters.put("offset", Integer.toString(offset));
        parameters.put("orderby", INSTANCES_ORDER_BY);
        parameters.put("includefield", INSTANCES_INCLUDE_FIELDS);

        return URI.create(endpointUrl + "?" + buildQueryString(parameters));
    }

    private static void validateCommon(String baseUrl, ViewerStudyRef ref) {
        Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        if (baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }
        Objects.requireNonNull(ref, "ref must not be null");
    }

    private static String normalizeStudiesRoot(String baseUrl) {
        String normalized = baseUrl.strip();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.endsWith(STUDIES_SEGMENT) ? normalized : normalized + STUDIES_SEGMENT;
    }

    private static String buildQueryString(Map<String, String> parameters) {
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            if (!query.isEmpty()) {
                query.append('&');
            }
            query.append(encodeRfc3986(entry.getKey()))
                .append('=')
                .append(encodeRfc3986(entry.getValue()));
        }
        return query.toString();
    }

    private static String encodeRfc3986(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
