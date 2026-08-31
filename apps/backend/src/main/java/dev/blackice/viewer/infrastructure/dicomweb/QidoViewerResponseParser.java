package dev.blackice.viewer.infrastructure.dicomweb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.blackice.viewer.application.result.InstanceIdentityMetadata;
import dev.blackice.viewer.application.result.SeriesMetadata;
import dev.blackice.viewer.application.result.StudyMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.dcm4che3.util.UIDUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses DICOMweb QIDO-RS JSON responses into viewer domain metadata records.
 */
@ApplicationScoped
public class QidoViewerResponseParser {

    /**
     * Signals a structurally invalid or malformed DICOM JSON response from QIDO-RS.
     */
    public static final class InvalidResponseException extends RuntimeException {
        public InvalidResponseException(String message) {
            super(message);
        }

        public InvalidResponseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final String STUDY_INSTANCE_UID = "0020000D";
    private static final String SERIES_INSTANCE_UID = "0020000E";
    private static final String SOP_INSTANCE_UID = "00080018";
    private static final String SOP_CLASS_UID = "00080016";
    private static final String PATIENT_NAME = "00100010";
    private static final String PATIENT_ID = "00100020";
    private static final String ISSUER_OF_PATIENT_ID = "00100021";
    private static final String STUDY_DATE = "00080020";
    private static final String STUDY_TIME = "00080030";
    private static final String STUDY_DESCRIPTION = "00081030";
    private static final String SERIES_NUMBER = "00200011";
    private static final String MODALITY = "00080060";
    private static final String SERIES_DESCRIPTION = "0008103E";
    private static final String NUMBER_OF_SERIES_RELATED_INSTANCES = "00201209";
    private static final String NUMBER_OF_FRAMES = "00280008";

    private static final Pattern TM_PATTERN = Pattern.compile("^(\\d{2})(?:(\\d{2})(?:(\\d{2})(\\.\\d{1,6})?)?)?$");

    private final ObjectMapper objectMapper;

    public QidoViewerResponseParser() {
        this(new ObjectMapper());
    }

    @Inject
    public QidoViewerResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * Parses a QIDO-RS single study query response into {@link StudyMetadata}.
     *
     * @param body raw DICOM JSON response body
     * @return parsed study metadata, or null if the array is empty
     * @throws InvalidResponseException if response is malformed, has invalid UIDs, or contains multiple studies
     */
    public StudyMetadata parseStudy(String body) {
        JsonNode root = readArrayRoot(body);
        if (root.isEmpty()) {
            return null;
        }
        if (root.size() > 1) {
            throw new InvalidResponseException("Multiple studies returned for single study query");
        }

        JsonNode dataset = root.get(0);
        if (!dataset.isObject()) {
            throw new InvalidResponseException("DICOM dataset item must be a JSON object");
        }

        String studyInstanceUid = requiredUid(dataset, STUDY_INSTANCE_UID);
        String patientName = personName(dataset);
        String patientId = firstText(dataset, PATIENT_ID, "LO");
        String patientIdIssuer = firstText(dataset, ISSUER_OF_PATIENT_ID, "LO");
        String studyDate = dicomDate(dataset);
        String studyTime = dicomTime(dataset);
        String description = firstText(dataset, STUDY_DESCRIPTION, "LO");

        return new StudyMetadata(
            studyInstanceUid,
            patientName,
            patientId,
            patientIdIssuer,
            studyDate,
            studyTime,
            description
        );
    }

    /**
     * Parses a QIDO-RS series query response into a list of {@link SeriesMetadata}.
     *
     * @param body raw DICOM JSON response body
     * @return immutable list of parsed series metadata
     * @throws InvalidResponseException if response is malformed, has duplicate or invalid UIDs
     */
    public List<SeriesMetadata> parseSeries(String body) {
        JsonNode root = readArrayRoot(body);
        if (root.isEmpty()) {
            return List.of();
        }

        List<SeriesMetadata> list = new ArrayList<>(root.size());
        Set<String> seenSeriesUids = new HashSet<>();

        for (JsonNode dataset : root) {
            if (!dataset.isObject()) {
                throw new InvalidResponseException("DICOM dataset item must be a JSON object");
            }

            String seriesInstanceUid = requiredUid(dataset, SERIES_INSTANCE_UID);
            if (!seenSeriesUids.add(seriesInstanceUid)) {
                throw new InvalidResponseException("Duplicate seriesInstanceUid in response");
            }

            Integer seriesNumber = integer(dataset, SERIES_NUMBER);
            String modality = firstText(dataset, MODALITY, "CS");
            String description = firstText(dataset, SERIES_DESCRIPTION, "LO");
            Integer instanceCount = integer(dataset, NUMBER_OF_SERIES_RELATED_INSTANCES);

            list.add(new SeriesMetadata(
                seriesInstanceUid,
                seriesNumber,
                modality,
                description,
                instanceCount
            ));
        }

        return List.copyOf(list);
    }

    /**
     * Parses a QIDO-RS instances query response page into a list of {@link InstanceIdentityMetadata}.
     *
     * @param body raw DICOM JSON response body
     * @return immutable list of parsed instance identity metadata
     * @throws InvalidResponseException if response is malformed, has duplicate or invalid UIDs, or malformed frames
     */
    public List<InstanceIdentityMetadata> parseInstances(String body) {
        JsonNode root = readArrayRoot(body);
        if (root.isEmpty()) {
            return List.of();
        }

        List<InstanceIdentityMetadata> list = new ArrayList<>(root.size());
        Set<String> seenSopUids = new HashSet<>();

        for (JsonNode dataset : root) {
            if (!dataset.isObject()) {
                throw new InvalidResponseException("DICOM dataset item must be a JSON object");
            }

            String seriesInstanceUid = requiredUid(dataset, SERIES_INSTANCE_UID);
            String sopInstanceUid = requiredUid(dataset, SOP_INSTANCE_UID);
            String sopClassUid = requiredUid(dataset, SOP_CLASS_UID);
            Integer numberOfFrames = numberOfFrames(dataset);

            if (!seenSopUids.add(sopInstanceUid)) {
                throw new InvalidResponseException("Duplicate sopInstanceUid in response");
            }

            list.add(new InstanceIdentityMetadata(
                seriesInstanceUid,
                sopInstanceUid,
                sopClassUid,
                numberOfFrames
            ));
        }

        return List.copyOf(list);
    }

    private JsonNode readArrayRoot(String body) {
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
        return root;
    }

    private JsonNode attribute(JsonNode dataset, String tag, String expectedVr) {
        JsonNode node = dataset.get(tag);
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (!node.isObject()) {
            throw new InvalidResponseException("Tag must be a JSON object");
        }
        JsonNode vrNode = node.get("vr");
        if (vrNode == null || !vrNode.isTextual()) {
            throw new InvalidResponseException("Tag missing valid vr field");
        }
        if (expectedVr != null && !expectedVr.equalsIgnoreCase(vrNode.asText().trim())) {
            throw new InvalidResponseException("Tag has unexpected vr");
        }
        return node;
    }

    private String requiredUid(JsonNode dataset, String tag) {
        JsonNode attr = attribute(dataset, tag, "UI");
        JsonNode values = attr == null ? null : attr.get("Value");
        if (values == null || !values.isArray() || values.size() != 1
            || !values.get(0).isTextual()) {
            throw new InvalidResponseException("Missing or invalid DICOM UID");
        }
        String value = values.get(0).asText();
        if (value.isEmpty() || !value.equals(value.strip()) || !UIDUtils.isValid(value)) {
            throw new InvalidResponseException("Missing or invalid DICOM UID");
        }
        return value;
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
            throw new InvalidResponseException("Tag Value must be an array");
        }
        if (val.isEmpty()) {
            return null;
        }
        JsonNode first = val.get(0);
        if (first == null || first.isNull()) {
            return null;
        }
        if (!first.isTextual()) {
            throw new InvalidResponseException("Tag Value item must be textual");
        }
        String text = first.asText().trim();
        return text.isEmpty() ? null : text;
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
            throw new InvalidResponseException("Value in tag must be an array");
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
                throw new InvalidResponseException("Invalid integer format in tag", e);
            }
        }
        throw new InvalidResponseException("Integer in tag must be a number or string");
    }

    private Integer numberOfFrames(JsonNode dataset) {
        JsonNode attr = attribute(dataset, NUMBER_OF_FRAMES, "IS");
        if (attr == null) {
            return null;
        }
        JsonNode val = attr.get("Value");
        if (val == null || val.isNull() || val.isMissingNode()) {
            return null;
        }
        if (!val.isArray()) {
            throw new InvalidResponseException("Value in NumberOfFrames must be an array");
        }
        if (val.isEmpty()) {
            return null;
        }
        JsonNode first = val.get(0);
        if (first == null || first.isNull()) {
            return null;
        }
        int frames;
        if (first.isIntegralNumber()) {
            frames = first.asInt();
        } else if (first.isTextual()) {
            try {
                frames = Integer.parseInt(first.asText().trim());
            } catch (NumberFormatException e) {
                throw new InvalidResponseException("Invalid integer format in NumberOfFrames", e);
            }
        } else {
            throw new InvalidResponseException("NumberOfFrames must be a number or string");
        }
        if (frames <= 0) {
            throw new InvalidResponseException("NumberOfFrames must be greater than zero");
        }
        return frames;
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
