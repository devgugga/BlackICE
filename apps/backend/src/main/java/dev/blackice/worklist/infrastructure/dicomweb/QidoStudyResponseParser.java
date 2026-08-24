package dev.blackice.worklist.infrastructure.dicomweb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.blackice.worklist.application.result.StudySummary;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.dcm4che3.util.UIDUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for DICOMweb QIDO-RS JSON responses (PS3.18 / DICOM JSON model).
 *
 * <p>Strictly validates required DICOM attributes such as StudyInstanceUID and parses standard
 * DICOM Value Representations (DA, TM, PN, CS, LO, IS, UI) according to DICOM Part 18 JSON model.</p>
 */
@ApplicationScoped
public class QidoStudyResponseParser {

    /** Signals a structurally invalid DICOM JSON response from QIDO-RS. */
    public static final class InvalidResponseException extends IllegalArgumentException {
        InvalidResponseException(String message) {
            super(message);
        }

        InvalidResponseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final String STUDY_INSTANCE_UID = "0020000D";
    private static final String PATIENT_NAME = "00100010";
    private static final String PATIENT_ID = "00100020";
    private static final String ISSUER_OF_PATIENT_ID = "00100021";
    private static final String STUDY_DATE = "00080020";
    private static final String STUDY_TIME = "00080030";
    private static final String MODALITIES_IN_STUDY = "00080061";
    private static final String STUDY_DESCRIPTION = "00081030";
    private static final String NUMBER_OF_STUDY_RELATED_SERIES = "00201206";
    private static final String NUMBER_OF_STUDY_RELATED_INSTANCES = "00201208";

    private static final Pattern TM_PATTERN = Pattern.compile("^(\\d{2})(?:(\\d{2})(?:(\\d{2})(\\.\\d{1,6})?)?)?$");

    private final ObjectMapper objectMapper;

    public QidoStudyResponseParser() {
        this(new ObjectMapper());
    }

    @Inject
    public QidoStudyResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * Parses a QIDO-RS DICOM JSON response string into a list of {@link StudySummary} instances.
     *
     * @param body raw DICOM JSON response string from the archive
     * @return immutable list of parsed study summaries
     * @throws InvalidResponseException if the response is malformed, missing mandatory attributes, or has invalid VRs
     */
    public List<StudySummary> parse(String body) {
        if (body == null || body.isBlank()) {
            throw new InvalidResponseException("QIDO-RS response body is required");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (JsonProcessingException e) {
            throw new InvalidResponseException("Invalid DICOM JSON response", e);
        }

        if (!root.isArray()) {
            throw new InvalidResponseException("DICOM JSON root must be a JSON array");
        }
        if (root.isEmpty()) {
            return List.of();
        }

        List<StudySummary> studies = new ArrayList<>(root.size());
        for (JsonNode dataset : root) {
            if (!dataset.isObject()) {
                throw new InvalidResponseException("DICOM dataset item must be a JSON object");
            }

            String studyInstanceUid = requireStudyUid(dataset);
            String patientName = personName(dataset);
            String patientId = firstText(dataset, PATIENT_ID, "LO");
            String patientIdIssuer = firstText(dataset, ISSUER_OF_PATIENT_ID, "LO");
            String studyDate = dicomDate(dataset);
            String studyTime = dicomTime(dataset);
            List<String> modalities = texts(dataset, MODALITIES_IN_STUDY, "CS");
            String description = firstText(dataset, STUDY_DESCRIPTION, "LO");
            Integer seriesCount = integer(dataset, NUMBER_OF_STUDY_RELATED_SERIES);
            Integer instanceCount = integer(dataset, NUMBER_OF_STUDY_RELATED_INSTANCES);

            studies.add(new StudySummary(
                studyInstanceUid,
                patientName,
                patientId,
                patientIdIssuer,
                studyDate,
                studyTime,
                modalities,
                description,
                seriesCount,
                instanceCount
            ));
        }

        return List.copyOf(studies);
    }

    private JsonNode attribute(JsonNode dataset, String tag, String expectedVr) {
        JsonNode node = dataset.get(tag);
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (!node.isObject()) {
            throw new InvalidResponseException("Tag " + tag + " must be a JSON object");
        }
        JsonNode vrNode = node.get("vr");
        if (vrNode == null || !vrNode.isTextual()) {
            throw new InvalidResponseException("Tag " + tag + " missing valid vr field");
        }
        if (expectedVr != null && !expectedVr.equalsIgnoreCase(vrNode.asText().trim())) {
            throw new InvalidResponseException(
                "Tag " + tag + " has invalid vr '" + vrNode.asText() + "', expected '" + expectedVr + "'");
        }
        return node;
    }

    private String requireStudyUid(JsonNode dataset) {
        JsonNode attr = attribute(dataset, STUDY_INSTANCE_UID, "UI");
        if (attr == null) {
            throw new InvalidResponseException("Missing mandatory StudyInstanceUID (0020,000D)");
        }
        JsonNode valueNode = attr.get("Value");
        if (valueNode == null || !valueNode.isArray() || valueNode.isEmpty()) {
            throw new InvalidResponseException("Missing Value array in StudyInstanceUID (0020,000D)");
        }
        JsonNode first = valueNode.get(0);
        if (first == null || !first.isTextual()) {
            throw new InvalidResponseException("StudyInstanceUID must be a string");
        }
        String uid = first.asText();
        if (!UIDUtils.isValid(uid)) {
            throw new InvalidResponseException("Invalid DICOM StudyInstanceUID");
        }
        return uid;
    }

    private String firstText(JsonNode dataset, String tag, String expectedVr) {
        JsonNode attr = attribute(dataset, tag, expectedVr);
        if (attr == null) {
            return null;
        }
        JsonNode val = attr.get("Value");
        if (val == null || val.isNull() || val.isMissingNode()) {
            return null;
        }
        if (!val.isArray()) {
            throw new InvalidResponseException("Value in tag " + tag + " must be an array");
        }
        if (val.isEmpty()) {
            return null;
        }
        JsonNode first = val.get(0);
        if (first == null || first.isNull()) {
            return null;
        }
        if (!first.isTextual()) {
            throw new InvalidResponseException("Value in tag " + tag + " must contain text strings");
        }
        String text = first.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private List<String> texts(JsonNode dataset, String tag, String expectedVr) {
        JsonNode attr = attribute(dataset, tag, expectedVr);
        if (attr == null) {
            return List.of();
        }
        JsonNode val = attr.get("Value");
        if (val == null || val.isNull() || val.isMissingNode()) {
            return List.of();
        }
        if (!val.isArray()) {
            throw new InvalidResponseException("Value in tag " + tag + " must be an array");
        }
        List<String> list = new ArrayList<>(val.size());
        for (JsonNode item : val) {
            if (item != null && !item.isNull()) {
                if (!item.isTextual()) {
                    throw new InvalidResponseException("Array item in tag " + tag + " must be a string");
                }
                list.add(item.asText().trim());
            }
        }
        return List.copyOf(list);
    }

    private String personName(JsonNode dataset) {
        JsonNode attr = attribute(dataset, PATIENT_NAME, "PN");
        if (attr == null) {
            return null;
        }
        JsonNode val = attr.get("Value");
        if (val == null || val.isNull() || val.isMissingNode()) {
            return null;
        }
        if (!val.isArray()) {
            throw new InvalidResponseException("Value in tag 00100010 must be an array");
        }
        if (val.isEmpty()) {
            return null;
        }
        JsonNode first = val.get(0);
        if (first == null || first.isNull()) {
            return null;
        }
        if (first.isTextual()) {
            return first.asText().trim().isEmpty() ? null : first.asText().trim();
        }
        if (first.isObject()) {
            if (first.hasNonNull("Alphabetic") && first.get("Alphabetic").isTextual()) {
                String name = first.get("Alphabetic").asText().trim();
                return name.isEmpty() ? null : name;
            }
            if (first.hasNonNull("Ideographic") && first.get("Ideographic").isTextual()) {
                String name = first.get("Ideographic").asText().trim();
                return name.isEmpty() ? null : name;
            }
            if (first.hasNonNull("Phonetic") && first.get("Phonetic").isTextual()) {
                String name = first.get("Phonetic").asText().trim();
                return name.isEmpty() ? null : name;
            }
            return null;
        }
        throw new InvalidResponseException("Person name Value item must be an object or string");
    }

    private Integer integer(JsonNode dataset, String tag) {
        JsonNode attr = attribute(dataset, tag, "IS");
        if (attr == null) {
            return null;
        }
        JsonNode val = attr.get("Value");
        if (val == null || val.isNull() || val.isMissingNode()) {
            return null;
        }
        if (!val.isArray()) {
            throw new InvalidResponseException("Value in tag " + tag + " must be an array");
        }
        if (val.isEmpty()) {
            return null;
        }
        JsonNode first = val.get(0);
        if (first == null || first.isNull()) {
            return null;
        }
        if (first.isIntegralNumber()) {
            return first.asInt();
        }
        if (first.isTextual()) {
            try {
                return Integer.parseInt(first.asText().trim());
            } catch (NumberFormatException e) {
                throw new InvalidResponseException("Invalid integer in tag " + tag, e);
            }
        }
        throw new InvalidResponseException("Integer in tag " + tag + " must be a number or string");
    }

    private String dicomDate(JsonNode dataset) {
        String rawDate = firstText(dataset, STUDY_DATE, "DA");
        if (rawDate == null) {
            return null;
        }
        if (!rawDate.matches("^\\d{8}$")) {
            throw new InvalidResponseException("Invalid DICOM DA format");
        }
        try {
            LocalDate date = LocalDate.parse(rawDate, DateTimeFormatter.BASIC_ISO_DATE);
            return DateTimeFormatter.ISO_LOCAL_DATE.format(date);
        } catch (DateTimeParseException e) {
            throw new InvalidResponseException("Invalid DICOM DA date", e);
        }
    }

    private String dicomTime(JsonNode dataset) {
        String rawTime = firstText(dataset, STUDY_TIME, "TM");
        if (rawTime == null) {
            return null;
        }
        Matcher matcher = TM_PATTERN.matcher(rawTime);
        if (!matcher.matches()) {
            throw new InvalidResponseException("Invalid DICOM TM format");
        }
        int hour = Integer.parseInt(matcher.group(1));
        if (hour < 0 || hour > 23) {
            throw new InvalidResponseException("Invalid hour in DICOM TM");
        }
        String minuteStr = matcher.group(2);
        if (minuteStr == null) {
            return matcher.group(1);
        }
        int minute = Integer.parseInt(minuteStr);
        if (minute < 0 || minute > 59) {
            throw new InvalidResponseException("Invalid minute in DICOM TM");
        }
        String secondStr = matcher.group(3);
        if (secondStr == null) {
            return matcher.group(1) + ":" + minuteStr;
        }
        int second = Integer.parseInt(secondStr);
        if (second < 0 || second > 60) {
            throw new InvalidResponseException("Invalid second in DICOM TM");
        }
        String fractionStr = matcher.group(4);
        if (fractionStr != null) {
            return matcher.group(1) + ":" + minuteStr + ":" + secondStr + fractionStr;
        }
        return matcher.group(1) + ":" + minuteStr + ":" + secondStr;
    }
}
