package dev.blackice.ingest.infrastructure.dicomweb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.blackice.ingest.application.result.StowInstanceResult;
import dev.blackice.ingest.application.result.StowStudyResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Parser for DICOMweb STOW-RS JSON responses (PS3.18 / DICOM JSON model).
 *
 * <p>DICOM Invariant: Reads Referenced SOP Sequence (0008,1199) and Failed SOP Sequence (0008,1198).
 * If a SOP UID appears in both sequences, the failure sequence takes precedence. Any submitted SOP UID
 * omitted from both sequences is strictly classified as {@link StowInstanceResult.Status#UNCONFIRMED}.</p>
 */
@ApplicationScoped
public class StowResponseParser {

    private static final String REFERENCED_SOP_SEQUENCE = "00081199";
    private static final String FAILED_SOP_SEQUENCE = "00081198";
    private static final String SOP_INSTANCE_UID = "00081155";
    private static final String FAILURE_REASON = "00081197";
    private static final String WARNING_REASON = "00081196";

    private final ObjectMapper objectMapper;

    public StowResponseParser() {
        this(new ObjectMapper());
    }

    @Inject
    public StowResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * Parses the archive STOW-RS response body against the submitted SOP Instance UIDs.
     *
     * @param studyInstanceUid exact Study Instance UID of the target study
     * @param body raw DICOM JSON response string from the archive
     * @param submittedSopUids set of SOP Instance UIDs that were submitted in the request
     * @return the resolved {@link StowStudyResult} with per-instance outcomes
     * @throws IllegalArgumentException if the response body cannot be parsed as valid JSON
     */
    public StowStudyResult parse(String studyInstanceUid, String body, Set<String> submittedSopUids) {
        Objects.requireNonNull(studyInstanceUid, "studyInstanceUid must not be null");
        Objects.requireNonNull(submittedSopUids, "submittedSopUids must not be null");

        Map<String, StowInstanceResult> parsedInstances = new LinkedHashMap<>();

        if (body != null && !body.isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(body);
                if (root.isArray() && !root.isEmpty()) {
                    for (JsonNode item : root) {
                        parseDataset(item, parsedInstances);
                    }
                } else if (root.isObject()) {
                    parseDataset(root, parsedInstances);
                }
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid DICOM JSON response", e);
            }
        }

        List<StowInstanceResult> instances = new ArrayList<>(submittedSopUids.size());
        for (String sopUid : submittedSopUids) {
            StowInstanceResult result = parsedInstances.get(sopUid);
            if (result != null) {
                instances.add(result);
            } else {
                instances.add(new StowInstanceResult(sopUid, StowInstanceResult.Status.UNCONFIRMED, null));
            }
        }

        return new StowStudyResult(studyInstanceUid, List.copyOf(instances));
    }

    private void parseDataset(JsonNode dataset, Map<String, StowInstanceResult> parsedInstances) {
        // First parse Referenced SOP Sequence (00081199)
        JsonNode refSeq = dataset.path(REFERENCED_SOP_SEQUENCE).path("Value");
        if (refSeq.isArray()) {
            for (JsonNode item : refSeq) {
                String sopUid = extractUid(item.path(SOP_INSTANCE_UID));
                if (sopUid != null && !sopUid.isBlank()) {
                    JsonNode warningNode = item.path(WARNING_REASON);
                    Integer warningReason = extractInt(warningNode);
                    if (warningReason != null || (!warningNode.isMissingNode() && !warningNode.isNull())) {
                        parsedInstances.put(sopUid, new StowInstanceResult(sopUid, StowInstanceResult.Status.WARNING, warningReason));
                    } else {
                        parsedInstances.put(sopUid, new StowInstanceResult(sopUid, StowInstanceResult.Status.ACCEPTED, null));
                    }
                }
            }
        }

        // Then parse Failed SOP Sequence (00081198) - overrides referenced sequence if contradictory
        JsonNode failedSeq = dataset.path(FAILED_SOP_SEQUENCE).path("Value");
        if (failedSeq.isArray()) {
            for (JsonNode item : failedSeq) {
                String sopUid = extractUid(item.path(SOP_INSTANCE_UID));
                if (sopUid != null && !sopUid.isBlank()) {
                    Integer failureReason = extractInt(item.path(FAILURE_REASON));
                    parsedInstances.put(sopUid, new StowInstanceResult(sopUid, StowInstanceResult.Status.REJECTED, failureReason));
                }
            }
        }
    }

    private static String extractUid(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode val = node.has("Value") ? node.path("Value") : node;
        if (val.isArray()) {
            if (val.isEmpty()) {
                return null;
            }
            val = val.get(0);
        }
        if (val == null || val.isNull() || val.isMissingNode()) {
            return null;
        }
        return val.isTextual() ? val.asText() : val.asText();
    }

    private static Integer extractInt(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode val = node.has("Value") ? node.path("Value") : node;
        if (val.isArray()) {
            if (val.isEmpty()) {
                return null;
            }
            val = val.get(0);
        }
        if (val == null || val.isNull() || val.isMissingNode()) {
            return null;
        }
        if (val.isNumber()) {
            return val.asInt();
        }
        if (val.isTextual()) {
            String text = val.asText().trim();
            if (text.isEmpty()) {
                return null;
            }
            try {
                if (text.startsWith("0x") || text.startsWith("0X")) {
                    return Integer.parseInt(text.substring(2), 16);
                }
                if (text.matches("(?i)^[0-9a-f]{4}$") && !text.matches("^[0-9]+$")) {
                    return Integer.parseInt(text, 16);
                }
                return Integer.parseInt(text);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
