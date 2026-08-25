package dev.blackice.viewer.infrastructure.dicomweb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.blackice.viewer.application.exception.InvalidArchiveMetadataException;
import dev.blackice.viewer.application.input.ViewerSeriesRef;
import dev.blackice.viewer.application.result.ViewerInstance;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.dcm4che3.util.UIDUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Parses DICOMweb WADO-RS Retrieve Series Metadata JSON responses into curated {@link ViewerInstance} records.
 */
@ApplicationScoped
public class WadoSeriesMetadataParser {

    private static final Set<String> SUPPORTED_IMAGE_SOP_CLASSES = Set.of(
        "1.2.840.10008.5.1.4.1.1.1",     // Computed Radiography Image Storage
        "1.2.840.10008.5.1.4.1.1.1.1",   // Digital X-Ray Image Storage - For Presentation
        "1.2.840.10008.5.1.4.1.1.2",     // CT Image Storage
        "1.2.840.10008.5.1.4.1.1.4"      // MR Image Storage
    );

    private static final String STUDY_INSTANCE_UID = "0020000D";
    private static final String SERIES_INSTANCE_UID = "0020000E";
    private static final String SOP_INSTANCE_UID = "00080018";
    private static final String SOP_CLASS_UID = "00080016";
    private static final String NUMBER_OF_FRAMES = "00280008";
    private static final String INSTANCE_NUMBER = "00200013";
    private static final String ROWS = "00280010";
    private static final String COLUMNS = "00280011";
    private static final String SAMPLES_PER_PIXEL = "00280002";
    private static final String PHOTOMETRIC_INTERPRETATION = "00280004";
    private static final String BITS_ALLOCATED = "00280100";
    private static final String BITS_STORED = "00280101";
    private static final String HIGH_BIT = "00280102";
    private static final String PIXEL_REPRESENTATION = "00280103";
    private static final String PLANAR_CONFIGURATION = "00280006";
    private static final String IMAGE_POSITION_PATIENT = "00200032";
    private static final String IMAGE_ORIENTATION_PATIENT = "00200037";
    private static final String PIXEL_SPACING = "00280030";
    private static final String FRAME_OF_REFERENCE_UID = "00200052";
    private static final String RESCALE_INTERCEPT = "00281052";
    private static final String RESCALE_SLOPE = "00281053";
    private static final String WINDOW_CENTER = "00281050";
    private static final String WINDOW_WIDTH = "00281051";

    private final ObjectMapper objectMapper;

    public WadoSeriesMetadataParser() {
        this(new ObjectMapper());
    }

    @Inject
    public WadoSeriesMetadataParser(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * Parses the WADO-RS series metadata JSON response into a list of curated {@link ViewerInstance} records.
     *
     * @param body raw DICOM JSON response body
     * @param seriesRef validated series reference
     * @return immutable list of parsed instances
     * @throws InvalidArchiveMetadataException if response is malformed, missing mandatory tags, has invalid UIDs,
     *                                         contains multi-frame instances, or non-allowlisted SOP classes
     */
    public List<ViewerInstance> parse(String body, ViewerSeriesRef seriesRef) {
        Objects.requireNonNull(seriesRef, "seriesRef must not be null");
        JsonNode root = readArrayRoot(body);
        if (root.isEmpty()) {
            return List.of();
        }

        List<ViewerInstance> instances = new ArrayList<>(root.size());
        Set<String> seenSopUids = new HashSet<>();

        for (JsonNode dataset : root) {
            if (!dataset.isObject()) {
                throw new InvalidArchiveMetadataException("Dataset item must be a JSON object");
            }

            // Verify study and series hierarchy
            String studyUid = requiredUid(dataset, STUDY_INSTANCE_UID);
            if (!seriesRef.studyInstanceUid().equals(studyUid)) {
                throw new InvalidArchiveMetadataException("StudyInstanceUID mismatch in metadata");
            }
            String seriesUid = requiredUid(dataset, SERIES_INSTANCE_UID);
            if (!seriesRef.seriesInstanceUid().equals(seriesUid)) {
                throw new InvalidArchiveMetadataException("SeriesInstanceUID mismatch in metadata");
            }

            String sopInstanceUid = requiredUid(dataset, SOP_INSTANCE_UID);
            if (!seenSopUids.add(sopInstanceUid)) {
                throw new InvalidArchiveMetadataException("Duplicate SOPInstanceUID in series metadata");
            }

            String sopClassUid = requiredUid(dataset, SOP_CLASS_UID);
            if (!SUPPORTED_IMAGE_SOP_CLASSES.contains(sopClassUid)) {
                throw new InvalidArchiveMetadataException("Non-allowlisted SOPClassUID");
            }

            // Reject multi-frame
            Integer numberOfFrames = optionalInteger(dataset, NUMBER_OF_FRAMES);
            if (numberOfFrames != null) {
                if (numberOfFrames <= 0 || numberOfFrames > 1) {
                    throw new InvalidArchiveMetadataException("Unsupported multi-frame or invalid numberOfFrames");
                }
            }

            Integer instanceNumber = optionalInteger(dataset, INSTANCE_NUMBER);
            Integer rows = requiredInteger(dataset, ROWS);
            Integer columns = requiredInteger(dataset, COLUMNS);
            Integer samplesPerPixel = requiredInteger(dataset, SAMPLES_PER_PIXEL);
            String photometricInterpretation = requiredString(dataset, PHOTOMETRIC_INTERPRETATION);
            Integer bitsAllocated = requiredInteger(dataset, BITS_ALLOCATED);
            Integer bitsStored = requiredInteger(dataset, BITS_STORED);
            Integer highBit = requiredInteger(dataset, HIGH_BIT);
            Integer pixelRepresentation = requiredInteger(dataset, PIXEL_REPRESENTATION);

            Integer planarConfiguration = null;
            if (samplesPerPixel > 1) {
                planarConfiguration = requiredInteger(dataset, PLANAR_CONFIGURATION);
            } else {
                planarConfiguration = optionalInteger(dataset, PLANAR_CONFIGURATION);
            }

            double[] imagePositionPatient = optionalDoubleArray(dataset, IMAGE_POSITION_PATIENT, 3, false);
            double[] imageOrientationPatient = optionalDoubleArray(dataset, IMAGE_ORIENTATION_PATIENT, 6, false);
            double[] pixelSpacing = optionalDoubleArray(dataset, PIXEL_SPACING, 2, true);
            String frameOfReferenceUid = optionalUid(dataset, FRAME_OF_REFERENCE_UID);
            Double rescaleIntercept = optionalDouble(dataset, RESCALE_INTERCEPT);
            Double rescaleSlope = optionalDouble(dataset, RESCALE_SLOPE);
            List<Double> windowCenter = optionalDoubleList(dataset, WINDOW_CENTER);
            List<Double> windowWidth = optionalDoubleList(dataset, WINDOW_WIDTH);

            instances.add(new ViewerInstance(
                sopInstanceUid,
                sopClassUid,
                instanceNumber,
                rows,
                columns,
                samplesPerPixel,
                photometricInterpretation,
                bitsAllocated,
                bitsStored,
                highBit,
                pixelRepresentation,
                planarConfiguration,
                imagePositionPatient,
                imageOrientationPatient,
                pixelSpacing,
                frameOfReferenceUid,
                rescaleIntercept,
                rescaleSlope,
                windowCenter,
                windowWidth
            ));
        }

        return List.copyOf(instances);
    }

    private JsonNode readArrayRoot(String body) {
        if (body == null || body.isBlank()) {
            throw new InvalidArchiveMetadataException("WADO-RS metadata body is required");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (JsonProcessingException e) {
            throw new InvalidArchiveMetadataException("Invalid DICOM JSON response", e);
        }
        if (!root.isArray()) {
            throw new InvalidArchiveMetadataException("DICOM JSON root must be an array");
        }
        return root;
    }

    private JsonNode attribute(JsonNode dataset, String tag) {
        JsonNode node = dataset.get(tag);
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (!node.isObject()) {
            throw new InvalidArchiveMetadataException("Tag node must be a JSON object");
        }
        return node;
    }

    private String requiredUid(JsonNode dataset, String tag) {
        String value = optionalUid(dataset, tag);
        if (value == null) {
            throw new InvalidArchiveMetadataException("Missing mandatory UID tag");
        }
        return value;
    }

    private String optionalUid(JsonNode dataset, String tag) {
        JsonNode attr = attribute(dataset, tag);
        if (attr == null) {
            return null;
        }
        JsonNode val = attr.get("Value");
        if (val == null || val.isNull() || val.isMissingNode() || !val.isArray() || val.isEmpty()) {
            return null;
        }
        JsonNode first = val.get(0);
        if (first == null || !first.isTextual()) {
            throw new InvalidArchiveMetadataException("UID Value must be textual");
        }
        String uid = first.asText().trim();
        if (uid.isEmpty()) {
            return null;
        }
        if (!UIDUtils.isValid(uid)) {
            throw new InvalidArchiveMetadataException("Malformed UID format");
        }
        return uid;
    }

    private String requiredString(JsonNode dataset, String tag) {
        String value = optionalString(dataset, tag);
        if (value == null) {
            throw new InvalidArchiveMetadataException("Missing mandatory string tag");
        }
        return value;
    }

    private String optionalString(JsonNode dataset, String tag) {
        JsonNode attr = attribute(dataset, tag);
        if (attr == null) {
            return null;
        }
        JsonNode val = attr.get("Value");
        if (val == null || val.isNull() || val.isMissingNode() || !val.isArray() || val.isEmpty()) {
            return null;
        }
        JsonNode first = val.get(0);
        if (first == null || !first.isTextual()) {
            throw new InvalidArchiveMetadataException("Value item must be textual");
        }
        String text = first.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private Integer requiredInteger(JsonNode dataset, String tag) {
        Integer value = optionalInteger(dataset, tag);
        if (value == null) {
            throw new InvalidArchiveMetadataException("Missing mandatory integer tag");
        }
        return value;
    }

    private Integer optionalInteger(JsonNode dataset, String tag) {
        JsonNode attr = attribute(dataset, tag);
        if (attr == null) {
            return null;
        }
        JsonNode val = attr.get("Value");
        if (val == null || val.isNull() || val.isMissingNode() || !val.isArray() || val.isEmpty()) {
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
                throw new InvalidArchiveMetadataException("Invalid integer format", e);
            }
        }
        throw new InvalidArchiveMetadataException("Integer value must be numeric or textual");
    }

    private Double optionalDouble(JsonNode dataset, String tag) {
        JsonNode attr = attribute(dataset, tag);
        if (attr == null) {
            return null;
        }
        JsonNode val = attr.get("Value");
        if (val == null || val.isNull() || val.isMissingNode() || !val.isArray() || val.isEmpty()) {
            return null;
        }
        JsonNode first = val.get(0);
        if (first == null || first.isNull()) {
            return null;
        }
        double d;
        if (first.isNumber()) {
            d = first.asDouble();
        } else if (first.isTextual()) {
            try {
                d = Double.parseDouble(first.asText().trim());
            } catch (NumberFormatException e) {
                throw new InvalidArchiveMetadataException("Invalid decimal format", e);
            }
        } else {
            throw new InvalidArchiveMetadataException("Decimal value must be numeric or textual");
        }
        if (!Double.isFinite(d)) {
            throw new InvalidArchiveMetadataException("Non-finite decimal value");
        }
        return d;
    }

    private List<Double> optionalDoubleList(JsonNode dataset, String tag) {
        JsonNode attr = attribute(dataset, tag);
        if (attr == null) {
            return null;
        }
        JsonNode val = attr.get("Value");
        if (val == null || val.isNull() || val.isMissingNode() || !val.isArray() || val.isEmpty()) {
            return null;
        }
        List<Double> list = new ArrayList<>(val.size());
        for (JsonNode item : val) {
            if (item == null || item.isNull()) {
                throw new InvalidArchiveMetadataException("Null item in decimal array");
            }
            double d;
            if (item.isNumber()) {
                d = item.asDouble();
            } else if (item.isTextual()) {
                try {
                    d = Double.parseDouble(item.asText().trim());
                } catch (NumberFormatException e) {
                    throw new InvalidArchiveMetadataException("Invalid decimal format in array", e);
                }
            } else {
                throw new InvalidArchiveMetadataException("Decimal array item must be numeric or textual");
            }
            if (!Double.isFinite(d)) {
                throw new InvalidArchiveMetadataException("Non-finite decimal in array");
            }
            list.add(d);
        }
        return List.copyOf(list);
    }

    private double[] optionalDoubleArray(JsonNode dataset, String tag, int expectedLength, boolean mustBePositive) {
        JsonNode attr = attribute(dataset, tag);
        if (attr == null) {
            return null;
        }
        JsonNode val = attr.get("Value");
        if (val == null || val.isNull() || val.isMissingNode() || !val.isArray() || val.isEmpty()) {
            return null;
        }
        if (val.size() != expectedLength) {
            throw new InvalidArchiveMetadataException("Unexpected array length for tag");
        }
        double[] array = new double[expectedLength];
        for (int i = 0; i < expectedLength; i++) {
            JsonNode item = val.get(i);
            if (item == null || item.isNull()) {
                throw new InvalidArchiveMetadataException("Null item in decimal array");
            }
            double d;
            if (item.isNumber()) {
                d = item.asDouble();
            } else if (item.isTextual()) {
                try {
                    d = Double.parseDouble(item.asText().trim());
                } catch (NumberFormatException e) {
                    throw new InvalidArchiveMetadataException("Invalid decimal format in array", e);
                }
            } else {
                throw new InvalidArchiveMetadataException("Decimal array item must be numeric or textual");
            }
            if (!Double.isFinite(d)) {
                throw new InvalidArchiveMetadataException("Non-finite decimal in array");
            }
            if (mustBePositive && d <= 0.0) {
                throw new InvalidArchiveMetadataException("Array value must be strictly positive");
            }
            array[i] = d;
        }
        return array;
    }
}
