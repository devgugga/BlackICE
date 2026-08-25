package dev.blackice.reports.infrastructure.dicomweb;

import dev.blackice.reports.application.input.ReportStudyRef;
import jakarta.enterprise.context.ApplicationScoped;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Builds deterministic QIDO-RS query URIs for checking study existence in a DICOMweb archive.
 */
@ApplicationScoped
public class ReportQidoQueryBuilder {

    private static final String STUDIES_PATH = "/studies";
    private static final String STUDY_INSTANCE_UID_FIELD = "0020000D";

    /**
     * Constructs a minimal QIDO-RS study existence query URI.
     *
     * @param baseUrl the base DICOMweb archive URL
     * @param study the validated study reference
     * @return the RFC 3986-compliant QIDO-RS URI
     */
    public URI build(String baseUrl, ReportStudyRef study) {
        Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        if (baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }
        Objects.requireNonNull(study, "study must not be null");

        String normalizedBase = baseUrl.strip();
        while (normalizedBase.endsWith("/")) {
            normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        }
        String endpointUrl = normalizedBase.endsWith(STUDIES_PATH) ? normalizedBase : normalizedBase + STUDIES_PATH;

        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("StudyInstanceUID", study.studyInstanceUid());
        parameters.put("limit", "1");
        parameters.put("includefield", STUDY_INSTANCE_UID_FIELD);

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

    private static String encodeRfc3986(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
