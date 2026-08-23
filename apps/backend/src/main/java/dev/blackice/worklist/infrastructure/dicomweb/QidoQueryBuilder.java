package dev.blackice.worklist.infrastructure.dicomweb;

import dev.blackice.worklist.application.input.StudySearchRequest;
import jakarta.enterprise.context.ApplicationScoped;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Builds deterministic QIDO-RS search URIs for querying studies from a DICOMweb archive.
 */
@ApplicationScoped
public class QidoQueryBuilder {

    private static final String STUDIES_PATH = "/studies";
    private static final String ORDER_BY_VALUE = "-StudyDate,-StudyTime,StudyInstanceUID";
    private static final String INCLUDE_FIELD_VALUE = "00100021,00081030,00201206,00201208";

    /**
     * Builds a target QIDO-RS URI containing curated query parameters and deterministic pagination/ordering.
     *
     * @param baseUrl the base DICOMweb URL of the archive (e.g. {@code http://host/dcm4chee-arc/aets/DCM4CHEE/rs})
     * @param request the validated study search criteria
     * @param fetchLimit the number of items to fetch in the QIDO query (e.g. pageSize + 1 for hasMore detection)
     * @return the constructed, RFC-3986-compliant QIDO-RS query URI
     */
    public URI build(String baseUrl, StudySearchRequest request, int fetchLimit) {
        Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        if (baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }
        Objects.requireNonNull(request, "request must not be null");
        if (fetchLimit <= 0) {
            throw new IllegalArgumentException("fetchLimit must be greater than 0");
        }

        String normalizedBase = baseUrl.strip();
        while (normalizedBase.endsWith("/")) {
            normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        }
        String endpointUrl = normalizedBase.endsWith(STUDIES_PATH) ? normalizedBase : normalizedBase + STUDIES_PATH;

        Map<String, String> parameters = new LinkedHashMap<>();
        if (request.patientName() != null) {
            parameters.put("PatientName", request.patientName() + "*");
        }
        if (request.patientId() != null) {
            parameters.put("PatientID", request.patientId());
        }
        if (request.modality() != null) {
            parameters.put("ModalitiesInStudy", request.modality());
        }
        String dateRange = studyDate(request);
        if (dateRange != null) {
            parameters.put("StudyDate", dateRange);
        }

        parameters.put("limit", Integer.toString(fetchLimit));
        parameters.put("offset", Integer.toString(request.offset()));
        parameters.put("orderby", ORDER_BY_VALUE);
        parameters.put("includefield", INCLUDE_FIELD_VALUE);

        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            if (!query.isEmpty()) {
                query.append('&');
            }
            query.append(encodeRfc3986(entry.getKey()))
                .append('=')
                .append(encodeRfc3986(entry.getValue()));
        }

        return URI.create(endpointUrl + "?" + query);
    }

    private String studyDate(StudySearchRequest request) {
        DateTimeFormatter dicomDate = DateTimeFormatter.BASIC_ISO_DATE;
        if (request.dateFrom() != null && request.dateTo() != null) {
            return dicomDate.format(request.dateFrom()) + "-" + dicomDate.format(request.dateTo());
        }
        if (request.dateFrom() != null) {
            return dicomDate.format(request.dateFrom()) + "-";
        }
        if (request.dateTo() != null) {
            return "-" + dicomDate.format(request.dateTo());
        }
        return null;
    }

    private static String encodeRfc3986(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
