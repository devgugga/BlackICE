package dev.blackice.viewer.infrastructure.dicomweb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import dev.blackice.viewer.application.exception.InvalidArchiveMetadataException;
import dev.blackice.viewer.application.input.ViewerSeriesRef;
import dev.blackice.viewer.application.result.ViewerInstance;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.dcm4che3.util.UIDUtils;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Parses DICOMweb WADO-RS Retrieve Series Metadata JSON responses into curated {@link ViewerInstance} records.
 */
@ApplicationScoped
public class WadoSeriesMetadataParser {

    private static final String CR_IMAGE_STORAGE = "1.2.840.10008.5.1.4.1.1.1";
    private static final String DX_IMAGE_STORAGE_FOR_PRESENTATION = "1.2.840.10008.5.1.4.1.1.1.1";
    private static final String CT_IMAGE_STORAGE = "1.2.840.10008.5.1.4.1.1.2";
    private static final String MR_IMAGE_STORAGE = "1.2.840.10008.5.1.4.1.1.4";

    private static final BigInteger MAX_UNSIGNED_SHORT = BigInteger.valueOf(65_535L);
    private static final Pattern INTEGER_STRING_PATTERN = Pattern.compile(" *[+-]?[0-9]+ *");
    private static final Pattern DECIMAL_STRING_PATTERN = Pattern.compile(
        " *[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[Ee][+-]?[0-9]+)? *"
    );

    private static final Set<String> SUPPORTED_IMAGE_SOP_CLASSES = Set.of(
        CR_IMAGE_STORAGE,
        DX_IMAGE_STORAGE_FOR_PRESENTATION,
        CT_IMAGE_STORAGE,
        MR_IMAGE_STORAGE
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
    private static final String VOI_LUT_FUNCTION = "00281056";

    private final ObjectMapper objectMapper;

    public WadoSeriesMetadataParser() {
        this(new ObjectMapper());
    }

    @Inject
    public WadoSeriesMetadataParser(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null")
            .copy()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .configure(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES, false);
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
            Integer numberOfFrames = optionalIntegerString(dataset, NUMBER_OF_FRAMES);
            if (numberOfFrames != null) {
                if (numberOfFrames <= 0 || numberOfFrames > 1) {
                    throw new InvalidArchiveMetadataException("Unsupported multi-frame or invalid numberOfFrames");
                }
            }

            Integer instanceNumber = optionalIntegerString(dataset, INSTANCE_NUMBER);
            Integer rows = requiredUnsignedShort(dataset, ROWS);
            Integer columns = requiredUnsignedShort(dataset, COLUMNS);
            Integer samplesPerPixel = requiredUnsignedShort(dataset, SAMPLES_PER_PIXEL);
            String photometricInterpretation = requiredString(dataset, PHOTOMETRIC_INTERPRETATION);
            Integer bitsAllocated = requiredUnsignedShort(dataset, BITS_ALLOCATED);
            Integer bitsStored = requiredUnsignedShort(dataset, BITS_STORED);
            Integer highBit = requiredUnsignedShort(dataset, HIGH_BIT);
            Integer pixelRepresentation = requiredUnsignedShort(dataset, PIXEL_REPRESENTATION);
            Integer planarConfiguration = optionalUnsignedShort(dataset, PLANAR_CONFIGURATION);

            validateImagePixelMetadata(
                sopClassUid,
                samplesPerPixel,
                photometricInterpretation,
                bitsAllocated,
                bitsStored,
                highBit,
                pixelRepresentation,
                planarConfiguration
            );

            double[] imagePositionPatient = optionalDoubleArray(dataset, IMAGE_POSITION_PATIENT, 3, false);
            double[] imageOrientationPatient = optionalDoubleArray(dataset, IMAGE_ORIENTATION_PATIENT, 6, false);
            double[] pixelSpacing = optionalDoubleArray(dataset, PIXEL_SPACING, 2, true);
            String frameOfReferenceUid = optionalUid(dataset, FRAME_OF_REFERENCE_UID);
            Double rescaleIntercept = optionalDouble(dataset, RESCALE_INTERCEPT);
            Double rescaleSlope = optionalDouble(dataset, RESCALE_SLOPE);
            List<Double> windowCenter = optionalDoubleList(dataset, WINDOW_CENTER);
            List<Double> windowWidth = optionalDoubleList(dataset, WINDOW_WIDTH);
            String voiLutFunction = optionalString(dataset, VOI_LUT_FUNCTION);

            boolean hasIntercept = rescaleIntercept != null;
            boolean hasSlope = rescaleSlope != null;
            if (hasIntercept != hasSlope || (hasSlope && rescaleSlope == 0.0d)
                || ((CT_IMAGE_STORAGE.equals(sopClassUid)
                    || DX_IMAGE_STORAGE_FOR_PRESENTATION.equals(sopClassUid)) && !hasIntercept)
                || (DX_IMAGE_STORAGE_FOR_PRESENTATION.equals(sopClassUid)
                    && (rescaleIntercept != 0.0d || rescaleSlope != 1.0d))) {
                throw new InvalidArchiveMetadataException("Invalid modality rescale metadata");
            }

            boolean hasWindowCenter = windowCenter != null;
            boolean hasWindowWidth = windowWidth != null;
            if (hasWindowCenter != hasWindowWidth
                || (hasWindowCenter && windowCenter.size() != windowWidth.size())
                || (hasWindowWidth && windowWidth.stream().anyMatch(width -> width < 1.0d))
                || (voiLutFunction != null && !"LINEAR".equals(voiLutFunction))) {
                throw new InvalidArchiveMetadataException("Invalid linear window metadata");
            }

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
        validateRawNumericLexemes(body);
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

    private void validateRawNumericLexemes(String body) {
        try (JsonParser parser = objectMapper.getFactory().createParser(body)) {
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                return;
            }
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                if (parser.currentToken() != JsonToken.START_OBJECT) {
                    parser.skipChildren();
                    continue;
                }
                validateDatasetNumericLexemes(parser);
            }
        } catch (IOException e) {
            throw new InvalidArchiveMetadataException("Invalid DICOM JSON response", e);
        }
    }

    private void validateDatasetNumericLexemes(JsonParser parser) throws IOException {
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (parser.currentToken() != JsonToken.FIELD_NAME) {
                parser.skipChildren();
                continue;
            }
            JsonToken attributeToken = parser.nextToken();
            if (attributeToken == JsonToken.START_OBJECT) {
                validateAttributeNumericLexemes(parser);
            } else {
                parser.skipChildren();
            }
        }
    }

    private void validateAttributeNumericLexemes(JsonParser parser) throws IOException {
        String vr = null;
        List<String> numericLexemes = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (parser.currentToken() != JsonToken.FIELD_NAME) {
                parser.skipChildren();
                continue;
            }
            String fieldName = parser.currentName();
            JsonToken valueToken = parser.nextToken();
            if ("vr".equals(fieldName) && valueToken == JsonToken.VALUE_STRING) {
                vr = parser.getText();
            } else if ("Value".equals(fieldName) && valueToken == JsonToken.START_ARRAY) {
                collectNumericLexemes(parser, numericLexemes);
            } else {
                parser.skipChildren();
            }
        }

        Pattern pattern = "DS".equals(vr)
            ? DECIMAL_STRING_PATTERN
            : "IS".equals(vr) ? INTEGER_STRING_PATTERN : null;
        int maximumLength = "DS".equals(vr) ? 16 : 12;
        if (pattern != null && numericLexemes.stream().anyMatch(
            lexical -> lexical.length() > maximumLength || !pattern.matcher(lexical).matches())) {
            throw new InvalidArchiveMetadataException("Invalid numeric DICOM lexical value");
        }
    }

    private void collectNumericLexemes(JsonParser parser, List<String> numericLexemes) throws IOException {
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (parser.currentToken().isNumeric()) {
                numericLexemes.add(parser.getText());
            } else {
                parser.skipChildren();
            }
        }
    }

    private JsonNode attribute(JsonNode dataset, String tag, String expectedVr) {
        JsonNode node = dataset.get(tag);
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (!node.isObject()) {
            throw new InvalidArchiveMetadataException("Tag node must be a JSON object");
        }
        JsonNode vr = node.get("vr");
        if (vr == null || !vr.isTextual() || !expectedVr.equals(vr.asText())) {
            throw new InvalidArchiveMetadataException("Tag has unexpected vr");
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
        JsonNode attr = attribute(dataset, tag, "UI");
        if (attr == null) {
            return null;
        }
        JsonNode val = attr.get("Value");
        if (val == null || !val.isArray() || val.size() != 1) {
            throw new InvalidArchiveMetadataException("UID Value must contain exactly one item");
        }
        JsonNode first = val.get(0);
        if (first == null || !first.isTextual()) {
            throw new InvalidArchiveMetadataException("UID Value must be textual");
        }
        String uid = first.asText();
        if (uid.isEmpty()) {
            throw new InvalidArchiveMetadataException("UID Value must not be empty");
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
        JsonNode attr = attribute(dataset, tag, "CS");
        if (attr == null) {
            return null;
        }
        JsonNode val = attr.get("Value");
        if (val == null || !val.isArray() || val.size() != 1) {
            throw new InvalidArchiveMetadataException("Value must contain exactly one item");
        }
        JsonNode first = val.get(0);
        if (first == null || !first.isTextual()) {
            throw new InvalidArchiveMetadataException("Value item must be textual");
        }
        String text = first.asText().trim();
        if (text.isEmpty()) {
            throw new InvalidArchiveMetadataException("Value item must not be empty");
        }
        return text;
    }

    private Integer requiredUnsignedShort(JsonNode dataset, String tag) {
        Integer value = optionalUnsignedShort(dataset, tag);
        if (value == null) {
            throw new InvalidArchiveMetadataException("Missing mandatory integer tag");
        }
        return value;
    }

    private Integer optionalUnsignedShort(JsonNode dataset, String tag) {
        JsonNode attr = attribute(dataset, tag, "US");
        if (attr == null) {
            return null;
        }
        JsonNode val = attr.get("Value");
        if (val == null || !val.isArray() || val.size() != 1) {
            throw new InvalidArchiveMetadataException("Integer Value must contain exactly one item");
        }
        JsonNode first = val.get(0);
        if (first == null || first.isNull()) {
            throw new InvalidArchiveMetadataException("Integer Value item must not be null");
        }
        if (!first.isIntegralNumber()) {
            throw new InvalidArchiveMetadataException("US Value must be an integral JSON Number");
        }
        BigInteger value = first.bigIntegerValue();
        if (value.signum() < 0 || value.compareTo(MAX_UNSIGNED_SHORT) > 0) {
            throw new InvalidArchiveMetadataException("US Value is outside its unsigned 16-bit range");
        }
        return value.intValue();
    }

    private Integer optionalIntegerString(JsonNode dataset, String tag) {
        JsonNode attr = attribute(dataset, tag, "IS");
        if (attr == null) {
            return null;
        }
        JsonNode val = attr.get("Value");
        if (val == null || !val.isArray() || val.size() != 1) {
            throw new InvalidArchiveMetadataException("Integer Value must contain exactly one item");
        }
        JsonNode first = val.get(0);
        if (first == null || (!first.isTextual() && !first.isIntegralNumber())) {
            throw new InvalidArchiveMetadataException("IS Value must be an integer Number or String");
        }
        String lexicalValue = first.asText();
        if (lexicalValue.length() > 12 || !INTEGER_STRING_PATTERN.matcher(lexicalValue).matches()) {
            throw new InvalidArchiveMetadataException("Invalid IS lexical value");
        }
        try {
            return Integer.valueOf(lexicalValue.trim());
        } catch (NumberFormatException e) {
            throw new InvalidArchiveMetadataException("IS Value is outside its signed 32-bit range", e);
        }
    }

    private Double optionalDouble(JsonNode dataset, String tag) {
        JsonNode attr = attribute(dataset, tag, "DS");
        if (attr == null) {
            return null;
        }
        JsonNode val = attr.get("Value");
        if (val == null || !val.isArray() || val.size() != 1) {
            throw new InvalidArchiveMetadataException("Decimal Value must contain exactly one item");
        }
        JsonNode first = val.get(0);
        if (first == null || first.isNull()) {
            throw new InvalidArchiveMetadataException("Decimal Value item must not be null");
        }
        return parseDecimalString(first);
    }

    private List<Double> optionalDoubleList(JsonNode dataset, String tag) {
        JsonNode attr = attribute(dataset, tag, "DS");
        if (attr == null) {
            return null;
        }
        JsonNode val = attr.get("Value");
        if (val == null || !val.isArray() || val.isEmpty()) {
            throw new InvalidArchiveMetadataException("Decimal Value must contain at least one item");
        }
        List<Double> list = new ArrayList<>(val.size());
        for (JsonNode item : val) {
            if (item == null || item.isNull()) {
                throw new InvalidArchiveMetadataException("Null item in decimal array");
            }
            list.add(parseDecimalString(item));
        }
        return List.copyOf(list);
    }

    private double[] optionalDoubleArray(JsonNode dataset, String tag, int expectedLength, boolean mustBePositive) {
        JsonNode attr = attribute(dataset, tag, "DS");
        if (attr == null) {
            return null;
        }
        JsonNode val = attr.get("Value");
        if (val == null || !val.isArray() || val.size() != expectedLength) {
            throw new InvalidArchiveMetadataException("Unexpected array length for tag");
        }
        double[] array = new double[expectedLength];
        for (int i = 0; i < expectedLength; i++) {
            JsonNode item = val.get(i);
            if (item == null || item.isNull()) {
                throw new InvalidArchiveMetadataException("Null item in decimal array");
            }
            double d = parseDecimalString(item);
            if (mustBePositive && d <= 0.0) {
                throw new InvalidArchiveMetadataException("Array value must be strictly positive");
            }
            array[i] = d;
        }
        return array;
    }

    private double parseDecimalString(JsonNode value) {
        if (!value.isTextual() && !value.isNumber()) {
            throw new InvalidArchiveMetadataException("DS Value must be a Number or String");
        }
        String lexicalValue = value.asText();
        if (lexicalValue.length() > 16 || !DECIMAL_STRING_PATTERN.matcher(lexicalValue).matches()) {
            throw new InvalidArchiveMetadataException("Invalid DS lexical value");
        }
        double decimal;
        try {
            decimal = Double.parseDouble(lexicalValue.trim());
        } catch (NumberFormatException e) {
            throw new InvalidArchiveMetadataException("Invalid DS numeric value", e);
        }
        if (!Double.isFinite(decimal)) {
            throw new InvalidArchiveMetadataException("Non-finite DS value");
        }
        return decimal;
    }

    private void validateImagePixelMetadata(
        String sopClassUid,
        int samplesPerPixel,
        String photometricInterpretation,
        int bitsAllocated,
        int bitsStored,
        int highBit,
        int pixelRepresentation,
        Integer planarConfiguration
    ) {
        boolean commonConstraintsValid = samplesPerPixel == 1
            && ("MONOCHROME1".equals(photometricInterpretation)
                || "MONOCHROME2".equals(photometricInterpretation))
            && bitsStored <= bitsAllocated
            && highBit == bitsStored - 1
            && (pixelRepresentation == 0 || pixelRepresentation == 1)
            && planarConfiguration == null;

        boolean sopClassConstraintsValid = switch (sopClassUid) {
            case CT_IMAGE_STORAGE -> bitsAllocated == 16 && bitsStored >= 12 && bitsStored <= 16;
            case MR_IMAGE_STORAGE -> bitsAllocated == 16;
            case CR_IMAGE_STORAGE -> bitsAllocated == 1 || (bitsAllocated > 0 && bitsAllocated % 8 == 0);
            case DX_IMAGE_STORAGE_FOR_PRESENTATION -> (bitsAllocated == 8 || bitsAllocated == 16)
                && bitsStored >= 6 && bitsStored <= 16 && pixelRepresentation == 0;
            default -> false;
        };

        if (!commonConstraintsValid || !sopClassConstraintsValid) {
            throw new InvalidArchiveMetadataException("Invalid Image Pixel metadata for SOP Class");
        }
    }
}
