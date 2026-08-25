package dev.blackice.reports.infrastructure.dicomweb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.blackice.reports.application.exception.ArchiveStudyLookupException;
import dev.blackice.reports.application.input.ReportStudyRef;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Objects;

/**
 * Parses DICOMweb QIDO-RS JSON responses to verify study existence.
 *
 * <p>Invariant: Only receives the expected StudyInstanceUID to confirm exact equality,
 * and never extracts or returns clinical metadata.</p>
 */
@ApplicationScoped
public class ReportQidoResponseParser {

    private static final String STUDY_INSTANCE_UID_TAG = "0020000D";
    private static final String EXPECTED_VR = "UI";

    private final ObjectMapper objectMapper;

    public ReportQidoResponseParser() {
        this(new ObjectMapper());
    }

    @Inject
    public ReportQidoResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * Parses a QIDO-RS response body to verify the existence of the expected study.
     *
     * @param body raw DICOM JSON response string
     * @param expectedStudy expected study reference
     * @return {@code true} if the study exists and matches; {@code false} if the response is empty
     * @throws ArchiveStudyLookupException with {@link ArchiveStudyLookupException.Reason#ARCHIVE_INVALID_RESPONSE}
     *         if the response is malformed, has unexpected attributes, or contains a mismatched UID
     */
    public boolean parse(String body, ReportStudyRef expectedStudy) {
        Objects.requireNonNull(expectedStudy, "expectedStudy must not be null");
        return parse(body, expectedStudy.studyInstanceUid());
    }

    /**
     * Parses a QIDO-RS response body to verify the existence of the expected StudyInstanceUID.
     *
     * @param body raw DICOM JSON response string
     * @param expectedStudyInstanceUid expected study instance UID string
     * @return {@code true} if the study exists and matches; {@code false} if the response is empty
     * @throws ArchiveStudyLookupException with {@link ArchiveStudyLookupException.Reason#ARCHIVE_INVALID_RESPONSE}
     *         if the response is malformed, has unexpected attributes, or contains a mismatched UID
     */
    public boolean parse(String body, String expectedStudyInstanceUid) {
        Objects.requireNonNull(expectedStudyInstanceUid, "expectedStudyInstanceUid must not be null");
        if (body == null || body.isBlank()) {
            throw new ArchiveStudyLookupException(ArchiveStudyLookupException.Reason.ARCHIVE_INVALID_RESPONSE);
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (JsonProcessingException e) {
            throw new ArchiveStudyLookupException(ArchiveStudyLookupException.Reason.ARCHIVE_INVALID_RESPONSE, e);
        }

        if (!root.isArray()) {
            throw new ArchiveStudyLookupException(ArchiveStudyLookupException.Reason.ARCHIVE_INVALID_RESPONSE);
        }

        if (root.isEmpty()) {
            return false;
        }

        if (root.size() > 1) {
            throw new ArchiveStudyLookupException(ArchiveStudyLookupException.Reason.ARCHIVE_INVALID_RESPONSE);
        }

        JsonNode dataset = root.get(0);
        if (dataset == null || !dataset.isObject()) {
            throw new ArchiveStudyLookupException(ArchiveStudyLookupException.Reason.ARCHIVE_INVALID_RESPONSE);
        }

        JsonNode tagNode = dataset.get(STUDY_INSTANCE_UID_TAG);
        if (tagNode == null || !tagNode.isObject()) {
            throw new ArchiveStudyLookupException(ArchiveStudyLookupException.Reason.ARCHIVE_INVALID_RESPONSE);
        }

        JsonNode vrNode = tagNode.get("vr");
        if (vrNode == null || !vrNode.isTextual() || !EXPECTED_VR.equalsIgnoreCase(vrNode.asText().trim())) {
            throw new ArchiveStudyLookupException(ArchiveStudyLookupException.Reason.ARCHIVE_INVALID_RESPONSE);
        }

        JsonNode valueNode = tagNode.get("Value");
        if (valueNode == null || !valueNode.isArray() || valueNode.isEmpty()) {
            throw new ArchiveStudyLookupException(ArchiveStudyLookupException.Reason.ARCHIVE_INVALID_RESPONSE);
        }

        JsonNode firstValue = valueNode.get(0);
        if (firstValue == null || !firstValue.isTextual()) {
            throw new ArchiveStudyLookupException(ArchiveStudyLookupException.Reason.ARCHIVE_INVALID_RESPONSE);
        }

        String actualUid = firstValue.asText();
        if (!expectedStudyInstanceUid.equals(actualUid)) {
            throw new ArchiveStudyLookupException(ArchiveStudyLookupException.Reason.ARCHIVE_INVALID_RESPONSE);
        }

        return true;
    }
}
