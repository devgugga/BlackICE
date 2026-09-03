package dev.blackice.ingest.infrastructure.dicomweb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.blackice.ingest.application.result.StowInstanceResult;
import dev.blackice.ingest.application.result.StowStudyResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.dcm4che3.util.UIDUtils;

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

    /** Signals a structurally invalid DICOM JSON response from STOW-RS. */
    public static final class InvalidResponseException extends IllegalArgumentException {
        InvalidResponseException(String message) {
            super(message);
        }

        InvalidResponseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

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
     * @throws InvalidResponseException if the response is not a structurally valid STOW-RS DICOM JSON result
     */
    public StowStudyResult parse(String studyInstanceUid, String body, Set<String> submittedSopUids) {
        Objects.requireNonNull(studyInstanceUid, "studyInstanceUid must not be null");
        Objects.requireNonNull(submittedSopUids, "submittedSopUids must not be null");

        if (body == null || body.isBlank()) {
            throw new InvalidResponseException("STOW-RS response body is required");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (JsonProcessingException e) {
            throw new InvalidResponseException("Invalid DICOM JSON response", e);
        }

        Map<String, StowInstanceResult> parsedInstances = new LinkedHashMap<>();
        if (root.isObject()) {
            parseDataset(root, parsedInstances);
        } else if (root.isArray() && !root.isEmpty()) {
            for (JsonNode item : root) {
                if (!item.isObject()) {
                    throw new InvalidResponseException("STOW-RS dataset item must be a JSON object");
                }
                parseDataset(item, parsedInstances);
            }
        } else {
            throw new InvalidResponseException("STOW-RS response root must be an object or non-empty array");
        }

        boolean confirmsSubmittedSop = submittedSopUids.stream().anyMatch(parsedInstances::containsKey);
        if (!confirmsSubmittedSop) {
            throw new InvalidResponseException("STOW-RS response confirms no submitted SOP Instance UID");
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
        boolean hasReferenced = dataset.has(REFERENCED_SOP_SEQUENCE);
        boolean hasFailed = dataset.has(FAILED_SOP_SEQUENCE);
        if (!hasReferenced && !hasFailed) {
            throw new InvalidResponseException("STOW-RS dataset has no result sequence");
        }

        if (hasReferenced) {
            for (JsonNode item : sequenceItems(dataset, REFERENCED_SOP_SEQUENCE)) {
                String sopUid = requireUid(item);
                JsonNode warningNode = item.path(WARNING_REASON);
                Integer warningReason = extractInt(warningNode);
                if (warningReason != null || (!warningNode.isMissingNode() && !warningNode.isNull())) {
                    mergeResult(parsedInstances,
                        new StowInstanceResult(sopUid, StowInstanceResult.Status.WARNING, warningReason));
                } else {
                    mergeResult(parsedInstances,
                        new StowInstanceResult(sopUid, StowInstanceResult.Status.ACCEPTED, null));
                }
            }
        }

        // Failed SOP Sequence is parsed last so rejection wins if the response is contradictory.
        if (hasFailed) {
            for (JsonNode item : sequenceItems(dataset, FAILED_SOP_SEQUENCE)) {
                String sopUid = requireUid(item);
                Integer failureReason = extractInt(item.path(FAILURE_REASON));
                mergeResult(parsedInstances,
                    new StowInstanceResult(sopUid, StowInstanceResult.Status.REJECTED, failureReason));
            }
        }
    }

    private static void mergeResult(Map<String, StowInstanceResult> results,
                                    StowInstanceResult candidate) {
        results.merge(candidate.sopInstanceUid(), candidate,
            (current, incoming) -> severity(incoming.status()) > severity(current.status())
                ? incoming : current);
    }

    private static int severity(StowInstanceResult.Status status) {
        return switch (status) {
            case ACCEPTED -> 0;
            case WARNING -> 1;
            case REJECTED -> 2;
            case UNCONFIRMED -> throw new IllegalArgumentException("UNCONFIRMED is not parsed from STOW");
        };
    }

    private static JsonNode sequenceItems(JsonNode dataset, String tag) {
        JsonNode attribute = dataset.get(tag);
        if (attribute == null || !attribute.isObject()) {
            throw new InvalidResponseException("STOW-RS sequence attribute must be an object");
        }
        JsonNode vr = attribute.get("vr");
        if (vr == null || !vr.isTextual() || !"SQ".equalsIgnoreCase(vr.asText())) {
            throw new InvalidResponseException("STOW-RS result sequence must use SQ VR");
        }
        JsonNode value = attribute.get("Value");
        if (value == null || !value.isArray()) {
            throw new InvalidResponseException("STOW-RS result sequence Value must be an array");
        }
        for (JsonNode item : value) {
            if (!item.isObject()) {
                throw new InvalidResponseException("STOW-RS sequence item must be a JSON object");
            }
        }
        return value;
    }

    private static String requireUid(JsonNode item) {
        JsonNode attribute = item.get(SOP_INSTANCE_UID);
        if (attribute == null || !attribute.isObject()) {
            throw new InvalidResponseException("STOW-RS sequence item is missing Referenced SOP Instance UID");
        }
        JsonNode vr = attribute.get("vr");
        JsonNode value = attribute.get("Value");
        if (vr == null || !vr.isTextual() || !"UI".equalsIgnoreCase(vr.asText())
            || value == null || !value.isArray() || value.isEmpty() || !value.get(0).isTextual()) {
            throw new InvalidResponseException("STOW-RS Referenced SOP Instance UID is malformed");
        }
        String uid = value.get(0).asText();
        if (!UIDUtils.isValid(uid)) {
            throw new InvalidResponseException("STOW-RS Referenced SOP Instance UID is invalid");
        }
        return uid;
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
