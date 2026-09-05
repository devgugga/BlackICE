package dev.blackice.viewer.infrastructure.dicomweb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.util.RawValue;
import dev.blackice.viewer.application.exception.InvalidArchiveMetadataException;
import dev.blackice.viewer.application.input.ViewerSeriesRef;
import dev.blackice.viewer.application.result.ViewerInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WadoSeriesMetadataParserTest {

    private static final String STUDY_UID = "1.2.840.113619.2.55.3.604688435.123.1599720123.467";
    private static final String SERIES_UID = "1.2.840.113619.2.55.3.604688435.124";
    private static final String SOP_UID = "1.2.840.113619.2.55.3.604688435.126";
    private static final String CR_SOP_CLASS = "1.2.840.10008.5.1.4.1.1.1";
    private static final String DX_SOP_CLASS = "1.2.840.10008.5.1.4.1.1.1.1";
    private static final String CT_SOP_CLASS = "1.2.840.10008.5.1.4.1.1.2";
    private static final String MR_SOP_CLASS = "1.2.840.10008.5.1.4.1.1.4";
    private static final String FOR_UID = "1.2.840.113619.2.55.3.604688435.127";

    private static final ViewerSeriesRef SERIES_REF = new ViewerSeriesRef(STUDY_UID, SERIES_UID);

    private WadoSeriesMetadataParser parser;

    @BeforeEach
    void setUp() {
        parser = new WadoSeriesMetadataParser();
    }

    @Test
    void parse_valid_ct_monochrome_instance_with_all_attributes() {
        String body = """
            [
              {
                "0020000D": { "vr": "UI", "Value": ["%s"] },
                "0020000E": { "vr": "UI", "Value": ["%s"] },
                "00080018": { "vr": "UI", "Value": ["%s"] },
                "00080016": { "vr": "UI", "Value": ["%s"] },
                "00200013": { "vr": "IS", "Value": [1] },
                "00280010": { "vr": "US", "Value": [512] },
                "00280011": { "vr": "US", "Value": [512] },
                "00280002": { "vr": "US", "Value": [1] },
                "00280004": { "vr": "CS", "Value": ["MONOCHROME2"] },
                "00280100": { "vr": "US", "Value": [16] },
                "00280101": { "vr": "US", "Value": [12] },
                "00280102": { "vr": "US", "Value": [11] },
                "00280103": { "vr": "US", "Value": [1] },
                "00200032": { "vr": "DS", "Value": [-127.5, -127.5, -100.0] },
                "00200037": { "vr": "DS", "Value": [1.0, 0.0, 0.0, 0.0, 1.0, 0.0] },
                "00280030": { "vr": "DS", "Value": [0.5, 0.5] },
                "00200052": { "vr": "UI", "Value": ["%s"] },
                "00281052": { "vr": "DS", "Value": [-1024.0] },
                "00281053": { "vr": "DS", "Value": [1.0] },
                "00281050": { "vr": "DS", "Value": [40.0] },
                "00281051": { "vr": "DS", "Value": [400.0] }
              }
            ]
            """.formatted(STUDY_UID, SERIES_UID, SOP_UID, CT_SOP_CLASS, FOR_UID);

        List<ViewerInstance> instances = parser.parse(body, SERIES_REF);
        assertEquals(1, instances.size());

        ViewerInstance image = instances.getFirst();
        assertEquals(SOP_UID, image.sopInstanceUid());
        assertEquals(CT_SOP_CLASS, image.sopClassUid());
        assertEquals(1, image.instanceNumber());
        assertEquals(512, image.rows());
        assertEquals(512, image.columns());
        assertEquals(1, image.samplesPerPixel());
        assertEquals("MONOCHROME2", image.photometricInterpretation());
        assertEquals(16, image.bitsAllocated());
        assertEquals(12, image.bitsStored());
        assertEquals(11, image.highBit());
        assertEquals(1, image.pixelRepresentation());
        assertNull(image.planarConfiguration());
        assertArrayEquals(new double[]{-127.5, -127.5, -100.0}, image.imagePositionPatient());
        assertArrayEquals(new double[]{1.0, 0.0, 0.0, 0.0, 1.0, 0.0}, image.imageOrientationPatient());
        assertArrayEquals(new double[]{0.5, 0.5}, image.pixelSpacing());
        assertEquals(FOR_UID, image.frameOfReferenceUid());
        assertEquals(-1024.0, image.rescaleIntercept());
        assertEquals(1.0, image.rescaleSlope());
        assertEquals(List.of(40.0), image.windowCenter());
        assertEquals(List.of(400.0), image.windowWidth());
    }

    @Test
    void parse_handles_dicom_string_encoded_is_ds_and_multivalued_window_values() {
        String body = """
            [
              {
                "0020000D": { "vr": "UI", "Value": ["%s"] },
                "0020000E": { "vr": "UI", "Value": ["%s"] },
                "00080018": { "vr": "UI", "Value": ["%s"] },
                "00080016": { "vr": "UI", "Value": ["%s"] },
                "00200013": { "vr": "IS", "Value": [" 42 "] },
                "00280010": { "vr": "US", "Value": [256] },
                "00280011": { "vr": "US", "Value": [256] },
                "00280002": { "vr": "US", "Value": [1] },
                "00280004": { "vr": "CS", "Value": ["MONOCHROME1"] },
                "00280100": { "vr": "US", "Value": [16] },
                "00280101": { "vr": "US", "Value": [8] },
                "00280102": { "vr": "US", "Value": [7] },
                "00280103": { "vr": "US", "Value": [0] },
                "00281050": { "vr": "DS", "Value": ["40.0", "50.5"] },
                "00281051": { "vr": "DS", "Value": ["400.0", "350.0"] }
              }
            ]
            """.formatted(STUDY_UID, SERIES_UID, SOP_UID, MR_SOP_CLASS);

        List<ViewerInstance> instances = parser.parse(body, SERIES_REF);
        assertEquals(1, instances.size());

        ViewerInstance image = instances.getFirst();
        assertEquals(42, image.instanceNumber());
        assertEquals(256, image.rows());
        assertEquals(256, image.columns());
        assertEquals(1, image.samplesPerPixel());
        assertEquals("MONOCHROME1", image.photometricInterpretation());
        assertEquals(16, image.bitsAllocated());
        assertEquals(8, image.bitsStored());
        assertEquals(7, image.highBit());
        assertEquals(0, image.pixelRepresentation());
        assertNull(image.pixelSpacing());
        assertNull(image.imagePositionPatient());
        assertNull(image.imageOrientationPatient());
        assertNull(image.frameOfReferenceUid());
        assertNull(image.rescaleIntercept());
        assertNull(image.rescaleSlope());
        assertEquals(List.of(40.0, 50.5), image.windowCenter());
        assertEquals(List.of(400.0, 350.0), image.windowWidth());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "1.2.840.10008.5.1.4.1.1.1",    // CR Image Storage
        "1.2.840.10008.5.1.4.1.1.1.1",  // Digital X-Ray Image Storage - For Presentation
        "1.2.840.10008.5.1.4.1.1.2",    // CT Image Storage
        "1.2.840.10008.5.1.4.1.1.4"     // MR Image Storage
    })
    void parse_accepts_all_four_allowlisted_sop_classes(String sopClassUid) {
        String body = baseDatasetJson(sopClassUid, SOP_UID);
        List<ViewerInstance> instances = parser.parse(body, SERIES_REF);
        assertEquals(1, instances.size());
        assertEquals(sopClassUid, instances.getFirst().sopClassUid());
    }

    @Test
    void parse_ignores_bulk_data_uri_and_other_tags() {
        String body = """
            [
              {
                "0020000D": { "vr": "UI", "Value": ["%s"] },
                "0020000E": { "vr": "UI", "Value": ["%s"] },
                "00080018": { "vr": "UI", "Value": ["%s"] },
                "00080016": { "vr": "UI", "Value": ["%s"] },
                "00280010": { "vr": "US", "Value": [512] },
                "00280011": { "vr": "US", "Value": [512] },
                "00280002": { "vr": "US", "Value": [1] },
                "00280004": { "vr": "CS", "Value": ["MONOCHROME2"] },
                "00280100": { "vr": "US", "Value": [16] },
                "00280101": { "vr": "US", "Value": [16] },
                "00280102": { "vr": "US", "Value": [15] },
                "00280103": { "vr": "US", "Value": [0] },
                "00281052": { "vr": "DS", "Value": [-1024] },
                "00281053": { "vr": "DS", "Value": [1] },
                "7FE00010": { "vr": "OB", "BulkDataURI": "http://arc:8080/dcm4chee-arc/aets/DCM4CHEE/rs/studies/1/series/2/instances/3/bulkdata" },
                "00080008": { "vr": "CS", "Value": ["ORIGINAL", "PRIMARY", "AXIAL"] }
              }
            ]
            """.formatted(STUDY_UID, SERIES_UID, SOP_UID, CT_SOP_CLASS);

        List<ViewerInstance> instances = parser.parse(body, SERIES_REF);
        assertEquals(1, instances.size());
        assertNotNull(instances.getFirst());
    }

    @Test
    void parse_rejects_color_and_planar_configuration_for_allowlisted_images() throws Exception {
        String colorBody = addAttribute(
            replaceValue(
                replaceValue(baseDatasetJson(CT_SOP_CLASS, SOP_UID), "00280002", "[3]"),
                "00280004",
                "[\"RGB\"]"),
            "00280006",
            "US",
            "[0]");
        assertThrows(InvalidArchiveMetadataException.class, () -> parser.parse(colorBody, SERIES_REF));

        String planarBody = addAttribute(
            baseDatasetJson(CT_SOP_CLASS, SOP_UID),
            "00280006",
            "US",
            "[0]");
        assertThrows(InvalidArchiveMetadataException.class, () -> parser.parse(planarBody, SERIES_REF));
    }

    @Test
    void parse_empty_array_returns_empty_list() {
        List<ViewerInstance> result = parser.parse("[]", SERIES_REF);
        assertTrue(result.isEmpty());
    }

    @Test
    void parse_rejects_null_or_blank_body() {
        assertThrows(InvalidArchiveMetadataException.class, () -> parser.parse(null, SERIES_REF));
        assertThrows(InvalidArchiveMetadataException.class, () -> parser.parse("", SERIES_REF));
        assertThrows(InvalidArchiveMetadataException.class, () -> parser.parse("   ", SERIES_REF));
    }

    @Test
    void parse_rejects_non_array_json() {
        assertThrows(InvalidArchiveMetadataException.class, () -> parser.parse("{}", SERIES_REF));
        assertThrows(InvalidArchiveMetadataException.class, () -> parser.parse("invalid json", SERIES_REF));
    }

    @Test
    void parse_rejects_mismatched_study_or_series_uid() {
        String mismatchedStudy = baseDatasetJson(CT_SOP_CLASS, SOP_UID, "1.2.3.999.mismatch", SERIES_UID);
        assertThrows(InvalidArchiveMetadataException.class, () -> parser.parse(mismatchedStudy, SERIES_REF));

        String mismatchedSeries = baseDatasetJson(CT_SOP_CLASS, SOP_UID, STUDY_UID, "1.2.3.999.mismatch");
        assertThrows(InvalidArchiveMetadataException.class, () -> parser.parse(mismatchedSeries, SERIES_REF));
    }

    @Test
    void parse_rejects_duplicate_sop_instance_uid() {
        String duplicateSop = """
            [
              {
                "0020000D": { "vr": "UI", "Value": ["%s"] },
                "0020000E": { "vr": "UI", "Value": ["%s"] },
                "00080018": { "vr": "UI", "Value": ["%s"] },
                "00080016": { "vr": "UI", "Value": ["%s"] },
                "00280010": { "vr": "US", "Value": [512] },
                "00280011": { "vr": "US", "Value": [512] },
                "00280002": { "vr": "US", "Value": [1] },
                "00280004": { "vr": "CS", "Value": ["MONOCHROME2"] },
                "00280100": { "vr": "US", "Value": [16] },
                "00280101": { "vr": "US", "Value": [16] },
                "00280102": { "vr": "US", "Value": [15] },
                "00280103": { "vr": "US", "Value": [0] }
              },
              {
                "0020000D": { "vr": "UI", "Value": ["%s"] },
                "0020000E": { "vr": "UI", "Value": ["%s"] },
                "00080018": { "vr": "UI", "Value": ["%s"] },
                "00080016": { "vr": "UI", "Value": ["%s"] },
                "00280010": { "vr": "US", "Value": [512] },
                "00280011": { "vr": "US", "Value": [512] },
                "00280002": { "vr": "US", "Value": [1] },
                "00280004": { "vr": "CS", "Value": ["MONOCHROME2"] },
                "00280100": { "vr": "US", "Value": [16] },
                "00280101": { "vr": "US", "Value": [16] },
                "00280102": { "vr": "US", "Value": [15] },
                "00280103": { "vr": "US", "Value": [0] }
              }
            ]
            """.formatted(STUDY_UID, SERIES_UID, SOP_UID, CT_SOP_CLASS, STUDY_UID, SERIES_UID, SOP_UID, CT_SOP_CLASS);

        assertThrows(InvalidArchiveMetadataException.class, () -> parser.parse(duplicateSop, SERIES_REF));
    }

    @Test
    void parse_rejects_multi_frame_instances() {
        String multiFrameBody = """
            [
              {
                "0020000D": { "vr": "UI", "Value": ["%s"] },
                "0020000E": { "vr": "UI", "Value": ["%s"] },
                "00080018": { "vr": "UI", "Value": ["%s"] },
                "00080016": { "vr": "UI", "Value": ["%s"] },
                "00280008": { "vr": "IS", "Value": [2] },
                "00280010": { "vr": "US", "Value": [512] },
                "00280011": { "vr": "US", "Value": [512] },
                "00280002": { "vr": "US", "Value": [1] },
                "00280004": { "vr": "CS", "Value": ["MONOCHROME2"] },
                "00280100": { "vr": "US", "Value": [16] },
                "00280101": { "vr": "US", "Value": [16] },
                "00280102": { "vr": "US", "Value": [15] },
                "00280103": { "vr": "US", "Value": [0] }
              }
            ]
            """.formatted(STUDY_UID, SERIES_UID, SOP_UID, CT_SOP_CLASS);

        assertThrows(InvalidArchiveMetadataException.class, () -> parser.parse(multiFrameBody, SERIES_REF));
    }

    @Test
    void parse_rejects_non_allowlisted_sop_class() {
        String secondaryCapture = "1.2.840.10008.5.1.4.1.1.7";
        String nonAllowlistedBody = baseDatasetJson(secondaryCapture, SOP_UID);
        assertThrows(InvalidArchiveMetadataException.class, () -> parser.parse(nonAllowlistedBody, SERIES_REF));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "00280010", // Rows
        "00280011", // Columns
        "00280002", // SamplesPerPixel
        "00280004", // PhotometricInterpretation
        "00280100", // BitsAllocated
        "00280101", // BitsStored
        "00280102", // HighBit
        "00280103", // PixelRepresentation
        "00080018", // SOPInstanceUID
        "00080016"  // SOPClassUID
    })
    void parse_rejects_missing_mandatory_attributes(String tagToRemove) {
        String body = datasetMissingTag(tagToRemove);
        assertThrows(InvalidArchiveMetadataException.class, () -> parser.parse(body, SERIES_REF));
    }

    @Test
    void parse_rejects_invalid_geometry_lengths() {
        // PixelSpacing with 1 element instead of 2
        String badPixelSpacing = """
            [
              {
                "0020000D": { "vr": "UI", "Value": ["%s"] },
                "0020000E": { "vr": "UI", "Value": ["%s"] },
                "00080018": { "vr": "UI", "Value": ["%s"] },
                "00080016": { "vr": "UI", "Value": ["%s"] },
                "00280010": { "vr": "US", "Value": [512] },
                "00280011": { "vr": "US", "Value": [512] },
                "00280002": { "vr": "US", "Value": [1] },
                "00280004": { "vr": "CS", "Value": ["MONOCHROME2"] },
                "00280100": { "vr": "US", "Value": [16] },
                "00280101": { "vr": "US", "Value": [16] },
                "00280102": { "vr": "US", "Value": [15] },
                "00280103": { "vr": "US", "Value": [0] },
                "00280030": { "vr": "DS", "Value": [0.5] }
              }
            ]
            """.formatted(STUDY_UID, SERIES_UID, SOP_UID, CT_SOP_CLASS);

        assertThrows(InvalidArchiveMetadataException.class, () -> parser.parse(badPixelSpacing, SERIES_REF));

        // ImagePositionPatient with 2 elements instead of 3
        String badIpp = """
            [
              {
                "0020000D": { "vr": "UI", "Value": ["%s"] },
                "0020000E": { "vr": "UI", "Value": ["%s"] },
                "00080018": { "vr": "UI", "Value": ["%s"] },
                "00080016": { "vr": "UI", "Value": ["%s"] },
                "00280010": { "vr": "US", "Value": [512] },
                "00280011": { "vr": "US", "Value": [512] },
                "00280002": { "vr": "US", "Value": [1] },
                "00280004": { "vr": "CS", "Value": ["MONOCHROME2"] },
                "00280100": { "vr": "US", "Value": [16] },
                "00280101": { "vr": "US", "Value": [16] },
                "00280102": { "vr": "US", "Value": [15] },
                "00280103": { "vr": "US", "Value": [0] },
                "00200032": { "vr": "DS", "Value": [0.0, 0.0] }
              }
            ]
            """.formatted(STUDY_UID, SERIES_UID, SOP_UID, CT_SOP_CLASS);

        assertThrows(InvalidArchiveMetadataException.class, () -> parser.parse(badIpp, SERIES_REF));

        // ImageOrientationPatient with 5 elements instead of 6
        String badIop = """
            [
              {
                "0020000D": { "vr": "UI", "Value": ["%s"] },
                "0020000E": { "vr": "UI", "Value": ["%s"] },
                "00080018": { "vr": "UI", "Value": ["%s"] },
                "00080016": { "vr": "UI", "Value": ["%s"] },
                "00280010": { "vr": "US", "Value": [512] },
                "00280011": { "vr": "US", "Value": [512] },
                "00280002": { "vr": "US", "Value": [1] },
                "00280004": { "vr": "CS", "Value": ["MONOCHROME2"] },
                "00280100": { "vr": "US", "Value": [16] },
                "00280101": { "vr": "US", "Value": [16] },
                "00280102": { "vr": "US", "Value": [15] },
                "00280103": { "vr": "US", "Value": [0] },
                "00200037": { "vr": "DS", "Value": [1.0, 0.0, 0.0, 0.0, 1.0] }
              }
            ]
            """.formatted(STUDY_UID, SERIES_UID, SOP_UID, CT_SOP_CLASS);

        assertThrows(InvalidArchiveMetadataException.class, () -> parser.parse(badIop, SERIES_REF));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidConsumedMetadata")
    void parse_rejects_invalid_consumed_attribute(String description, String body) {
        assertThrows(InvalidArchiveMetadataException.class, () -> parser.parse(body, SERIES_REF));
    }

    static Stream<Arguments> invalidConsumedMetadata() throws JsonProcessingException {
        String base = baseDatasetJsonStatic(CT_SOP_CLASS, SOP_UID, STUDY_UID, SERIES_UID);
        return Stream.of(
            Arguments.of("StudyInstanceUID with LO vr", replaceVr(base, "0020000D", "LO")),
            Arguments.of("StudyInstanceUID with VM 2", replaceValue(base, "0020000D", "[\"" + STUDY_UID + "\",\"1.2.3\"]")),
            Arguments.of("Rows with IS vr", replaceVr(base, "00280010", "IS")),
            Arguments.of("InstanceNumber with null value", replaceValue(base, "00200013", "[null]")),
            Arguments.of("ImagePositionPatient with FL vr", replaceVr(base, "00200032", "FL")),
            Arguments.of("ImagePositionPatient with VM 2", replaceValue(base, "00200032", "[0,0]")),
            Arguments.of("FrameOfReferenceUID with empty value", replaceValue(base, "00200052", "[\"\"]")),
            Arguments.of("WindowCenter with IS vr", replaceVr(base, "00281050", "IS")),
            Arguments.of("RescaleSlope with VM 2", replaceValue(base, "00281053", "[1,2]"))
        );
    }

    @Test
    void rejects_ct_without_rescale_pair() throws Exception {
        assertThrows(InvalidArchiveMetadataException.class,
            () -> parser.parse(removeTag(baseDatasetJson(CT_SOP_CLASS, SOP_UID), "00281052"), SERIES_REF));
        assertThrows(InvalidArchiveMetadataException.class,
            () -> parser.parse(removeTag(baseDatasetJson(CT_SOP_CLASS, SOP_UID), "00281053"), SERIES_REF));
    }

    @ParameterizedTest
    @ValueSource(strings = {CR_SOP_CLASS, MR_SOP_CLASS})
    void preserves_absent_rescale_pair_for_cr_and_mr(String sopClassUid) throws Exception {
        String body = removeTag(
            removeTag(baseDatasetJson(sopClassUid, SOP_UID), "00281052"),
            "00281053");
        ViewerInstance instance = parser.parse(body, SERIES_REF).getFirst();
        assertNull(instance.rescaleIntercept());
        assertNull(instance.rescaleSlope());
    }

    @Test
    void rejects_zero_rescale_slope() throws Exception {
        String body = replaceValue(baseDatasetJson(CT_SOP_CLASS, SOP_UID), "00281053", "[0]");
        assertThrows(InvalidArchiveMetadataException.class,
            () -> parser.parse(body, SERIES_REF));
    }

    @Test
    void requires_identity_rescale_for_dx_for_presentation() throws Exception {
        ViewerInstance valid = parser.parse(baseDatasetJson(DX_SOP_CLASS, SOP_UID), SERIES_REF).getFirst();
        assertEquals(0.0, valid.rescaleIntercept());
        assertEquals(1.0, valid.rescaleSlope());

        String invalidIntercept = replaceValue(
            baseDatasetJson(DX_SOP_CLASS, SOP_UID),
            "00281052",
            "[-1024]");
        assertThrows(InvalidArchiveMetadataException.class,
            () -> parser.parse(invalidIntercept, SERIES_REF));

        String missingPair = removeTag(
            removeTag(baseDatasetJson(DX_SOP_CLASS, SOP_UID), "00281052"),
            "00281053");
        assertThrows(InvalidArchiveMetadataException.class,
            () -> parser.parse(missingPair, SERIES_REF));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("validImagePixelMetadata")
    void accepts_standard_image_pixel_constraints(String description, String body) {
        assertEquals(1, parser.parse(body, SERIES_REF).size());
    }

    static Stream<Arguments> validImagePixelMetadata() throws JsonProcessingException {
        String ct = replaceValues(
            baseDatasetJsonStatic(CT_SOP_CLASS, SOP_UID, STUDY_UID, SERIES_UID),
            "00280004", "[\"MONOCHROME1\"]",
            "00280101", "[12]",
            "00280102", "[11]",
            "00280103", "[1]");
        String mr = replaceValues(
            baseDatasetJsonStatic(MR_SOP_CLASS, SOP_UID, STUDY_UID, SERIES_UID),
            "00280101", "[8]",
            "00280102", "[7]",
            "00280103", "[1]");
        String cr = replaceValues(
            baseDatasetJsonStatic(CR_SOP_CLASS, SOP_UID, STUDY_UID, SERIES_UID),
            "00280100", "[8]",
            "00280101", "[8]",
            "00280102", "[7]",
            "00280103", "[1]");
        cr = removeTag(removeTag(cr, "00281052"), "00281053");
        String dx = replaceValues(
            baseDatasetJsonStatic(DX_SOP_CLASS, SOP_UID, STUDY_UID, SERIES_UID),
            "00280100", "[8]",
            "00280101", "[6]",
            "00280102", "[5]");
        return Stream.of(
            Arguments.of("CT specialization", ct),
            Arguments.of("MR specialization", mr),
            Arguments.of("CR specialization and common Image Pixel constraints", cr),
            Arguments.of("DX For Presentation specialization", dx)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidImagePixelMetadata")
    void rejects_image_pixel_values_outside_sop_class_constraints(String description, String body) {
        assertThrows(InvalidArchiveMetadataException.class, () -> parser.parse(body, SERIES_REF));
    }

    static Stream<Arguments> invalidImagePixelMetadata() throws JsonProcessingException {
        String ct = baseDatasetJsonStatic(CT_SOP_CLASS, SOP_UID, STUDY_UID, SERIES_UID);
        String mr = baseDatasetJsonStatic(MR_SOP_CLASS, SOP_UID, STUDY_UID, SERIES_UID);
        String cr = baseDatasetJsonStatic(CR_SOP_CLASS, SOP_UID, STUDY_UID, SERIES_UID);
        String dx = baseDatasetJsonStatic(DX_SOP_CLASS, SOP_UID, STUDY_UID, SERIES_UID);
        return Stream.of(
            Arguments.of("CT samples per pixel", replaceValue(ct, "00280002", "[3]")),
            Arguments.of("CT photometric interpretation", replaceValue(ct, "00280004", "[\"RGB\"]")),
            Arguments.of("CT bits allocated", replaceValue(ct, "00280100", "[8]")),
            Arguments.of("CT bits stored", replaceValue(ct, "00280101", "[11]")),
            Arguments.of("CT high bit", replaceValue(ct, "00280102", "[14]")),
            Arguments.of("CT pixel representation", replaceValue(ct, "00280103", "[2]")),
            Arguments.of("MR bits allocated", replaceValue(mr, "00280100", "[8]")),
            Arguments.of("MR bits stored above allocation", replaceValue(mr, "00280101", "[17]")),
            Arguments.of("MR high bit", replaceValue(mr, "00280102", "[14]")),
            Arguments.of("MR pixel representation", replaceValue(mr, "00280103", "[2]")),
            Arguments.of("CR non-standard bits allocated", replaceValue(cr, "00280100", "[7]")),
            Arguments.of("CR bits stored above allocation", replaceValue(cr, "00280101", "[17]")),
            Arguments.of("CR high bit", replaceValue(cr, "00280102", "[14]")),
            Arguments.of("CR pixel representation", replaceValue(cr, "00280103", "[2]")),
            Arguments.of("DX bits allocated", replaceValue(dx, "00280100", "[32]")),
            Arguments.of("DX bits stored below six", replaceValue(dx, "00280101", "[5]")),
            Arguments.of("DX bits stored above allocation", replaceValues(
                dx, "00280100", "[8]", "00280101", "[9]", "00280102", "[8]")),
            Arguments.of("DX high bit", replaceValue(dx, "00280102", "[14]")),
            Arguments.of("DX signed pixel representation", replaceValue(dx, "00280103", "[1]"))
        );
    }

    @Test
    void validates_coupled_linear_window_metadata() throws Exception {
        String base = baseDatasetJson(CT_SOP_CLASS, SOP_UID);
        assertThrows(InvalidArchiveMetadataException.class,
            () -> parser.parse(removeTag(base, "00281050"), SERIES_REF));
        assertThrows(InvalidArchiveMetadataException.class,
            () -> parser.parse(removeTag(base, "00281051"), SERIES_REF));
        assertThrows(InvalidArchiveMetadataException.class,
            () -> parser.parse(replaceValue(base, "00281050", "[40,80]"), SERIES_REF));
        assertThrows(InvalidArchiveMetadataException.class,
            () -> parser.parse(replaceValue(base, "00281051", "[0]"), SERIES_REF));
        assertThrows(InvalidArchiveMetadataException.class,
            () -> parser.parse(replaceValue(base, "00281051", "[-1]"), SERIES_REF));
    }

    @Test
    void rejects_untransported_voi_lut_function_but_accepts_explicit_linear() throws Exception {
        String sigmoid = addAttribute(
            baseDatasetJson(CT_SOP_CLASS, SOP_UID),
            "00281056",
            "CS",
            "[\"SIGMOID\"]");
        assertThrows(InvalidArchiveMetadataException.class,
            () -> parser.parse(sigmoid, SERIES_REF));

        String linear = addAttribute(
            baseDatasetJson(CT_SOP_CLASS, SOP_UID),
            "00281056",
            "CS",
            "[\"LINEAR\"]");
        assertEquals(1, parser.parse(linear, SERIES_REF).size());
    }

    @ParameterizedTest
    @ValueSource(strings = {CT_SOP_CLASS, MR_SOP_CLASS, CR_SOP_CLASS, DX_SOP_CLASS})
    void rejects_voi_lut_sequence_without_supported_linear_window(String sopClassUid) throws Exception {
        String unsignedImage = replaceValue(
            baseDatasetJson(sopClassUid, SOP_UID),
            "00280103",
            "[0]");
        String lutOnly = addValidVoiLutSequence(
            removeTag(removeTag(unsignedImage, "00281050"), "00281051")
        );

        assertThrows(InvalidArchiveMetadataException.class,
            () -> parser.parse(lutOnly, SERIES_REF));
    }

    @Test
    void accepts_voi_lut_sequence_when_supported_linear_window_is_also_present() throws Exception {
        String body = addValidVoiLutSequence(
            replaceValue(baseDatasetJson(CT_SOP_CLASS, SOP_UID), "00280103", "[0]"));

        assertEquals(1, parser.parse(body, SERIES_REF).size());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidUsValues")
    void rejects_invalid_us_json_values(String description, String body) {
        assertThrows(InvalidArchiveMetadataException.class, () -> parser.parse(body, SERIES_REF));
    }

    static Stream<Arguments> invalidUsValues() throws JsonProcessingException {
        String base = baseDatasetJsonStatic(CT_SOP_CLASS, SOP_UID, STUDY_UID, SERIES_UID);
        return Stream.of(
            Arguments.of("US string", replaceValue(base, "00280010", "[\"512\"]")),
            Arguments.of("US decimal", replaceValue(base, "00280010", "[1.0]")),
            Arguments.of("US negative", replaceValue(base, "00280010", "[-1]")),
            Arguments.of("US above range", replaceValue(base, "00280010", "[65536]")),
            Arguments.of("US integer overflow", replaceValue(base, "00280010", "[9223372036854775808]"))
        );
    }

    @Test
    void accepts_maximum_us_value() throws Exception {
        String body = replaceValue(baseDatasetJson(CT_SOP_CLASS, SOP_UID), "00280010", "[65535]");
        assertEquals(65535, parser.parse(body, SERIES_REF).getFirst().rows());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidIsValues")
    void rejects_invalid_is_lexical_or_numeric_values(String description, String body) {
        assertThrows(InvalidArchiveMetadataException.class, () -> parser.parse(body, SERIES_REF));
    }

    static Stream<Arguments> invalidIsValues() throws JsonProcessingException {
        String base = baseDatasetJsonStatic(CT_SOP_CLASS, SOP_UID, STUDY_UID, SERIES_UID);
        return Stream.of(
            Arguments.of("IS decimal string", replaceValue(base, "00200013", "[\"1.0\"]")),
            Arguments.of("IS embedded space", replaceValue(base, "00200013", "[\"1 2\"]")),
            Arguments.of("IS longer than 12 bytes", replaceValue(base, "00200013", "[\"0000000000001\"]")),
            Arguments.of("IS JSON decimal", replaceValue(base, "00200013", "[1.0]")),
            Arguments.of("IS above signed 32-bit range", replaceValue(base, "00200013", "[2147483648]")),
            Arguments.of("IS integer overflow", replaceValue(base, "00200013", "[9223372036854775808]"))
        );
    }

    @Test
    void accepts_padded_and_boundary_is_values() throws Exception {
        String padded = replaceValue(baseDatasetJson(CT_SOP_CLASS, SOP_UID), "00200013", "[\" +1 \"]");
        assertEquals(1, parser.parse(padded, SERIES_REF).getFirst().instanceNumber());

        String minimum = replaceValue(
            baseDatasetJson(CT_SOP_CLASS, SOP_UID),
            "00200013",
            "[\"-2147483648\"]");
        assertEquals(Integer.MIN_VALUE, parser.parse(minimum, SERIES_REF).getFirst().instanceNumber());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidDsValues")
    void rejects_invalid_ds_lexical_values(String description, String body) {
        assertThrows(InvalidArchiveMetadataException.class, () -> parser.parse(body, SERIES_REF));
    }

    static Stream<Arguments> invalidDsValues() throws JsonProcessingException {
        String base = baseDatasetJsonStatic(CT_SOP_CLASS, SOP_UID, STUDY_UID, SERIES_UID);
        return Stream.of(
            Arguments.of("DS embedded space", replaceValue(base, "00281052", "[\"1 2\"]")),
            Arguments.of("DS incomplete exponent", replaceValue(base, "00281052", "[\"1E\"]")),
            Arguments.of("DS missing digits", replaceValue(base, "00281052", "[\".\"]")),
            Arguments.of("DS longer than 16 bytes string", replaceValue(
                base, "00281052", "[\"12345678901234567\"]")),
            Arguments.of("DS longer than 16 bytes number", replaceValue(
                base, "00281052", "[12345678901234567]")),
            Arguments.of("DS longer than 16 bytes decimal number", replaceValue(
                base, "00281052", "[1.000000000000000]")),
            Arguments.of("DS longer than 16 bytes exponent number", replaceValueWithRawNumber(
                base, "00281052", "1e+00000000000001")),
            Arguments.of("DS non-finite overflow", replaceValue(base, "00281052", "[\"1E309\"]"))
        );
    }

    @Test
    void accepts_standard_ds_lexical_forms() throws Exception {
        String body = replaceValues(
            baseDatasetJson(CT_SOP_CLASS, SOP_UID),
            "00281052", "[\" +1.25E-3 \"]",
            "00281053", "[\".5\"]",
            "00281050", "[\"1.\"]",
            "00281051", "[\"1\"]");
        ViewerInstance instance = parser.parse(body, SERIES_REF).getFirst();
        assertEquals(0.00125, instance.rescaleIntercept());
        assertEquals(0.5, instance.rescaleSlope());
        assertEquals(List.of(1.0), instance.windowCenter());
        assertEquals(List.of(1.0), instance.windowWidth());
    }

    private String baseDatasetJson(String sopClassUid, String sopInstanceUid) {
        return baseDatasetJson(sopClassUid, sopInstanceUid, STUDY_UID, SERIES_UID);
    }

    private String baseDatasetJson(String sopClassUid, String sopInstanceUid, String studyUid, String seriesUid) {
        return baseDatasetJsonStatic(sopClassUid, sopInstanceUid, studyUid, seriesUid);
    }

    private static String baseDatasetJsonStatic(
        String sopClassUid,
        String sopInstanceUid,
        String studyUid,
        String seriesUid
    ) {
        int rescaleIntercept = DX_SOP_CLASS.equals(sopClassUid) ? 0 : -1024;
        return """
            [
              {
                "0020000D": { "vr": "UI", "Value": ["%s"] },
                "0020000E": { "vr": "UI", "Value": ["%s"] },
                "00080018": { "vr": "UI", "Value": ["%s"] },
                "00080016": { "vr": "UI", "Value": ["%s"] },
                "00280010": { "vr": "US", "Value": [512] },
                "00280011": { "vr": "US", "Value": [512] },
                "00280002": { "vr": "US", "Value": [1] },
                "00280004": { "vr": "CS", "Value": ["MONOCHROME2"] },
                "00280100": { "vr": "US", "Value": [16] },
                "00280101": { "vr": "US", "Value": [16] },
                "00280102": { "vr": "US", "Value": [15] },
                "00280103": { "vr": "US", "Value": [0] },
                "00200032": { "vr": "DS", "Value": [0, 0, 0] },
                "00200052": { "vr": "UI", "Value": ["%s"] },
                "00200013": { "vr": "IS", "Value": [1] },
                "00281052": { "vr": "DS", "Value": [%d] },
                "00281053": { "vr": "DS", "Value": [1] },
                "00281050": { "vr": "DS", "Value": [40] },
                "00281051": { "vr": "DS", "Value": [400] }
              }
            ]
            """.formatted(studyUid, seriesUid, sopInstanceUid, sopClassUid, FOR_UID, rescaleIntercept);
    }

    private String datasetMissingTag(String tagToRemove) {
        StringBuilder sb = new StringBuilder("[\n  {\n");
        appendTagIfDifferent(sb, "0020000D", "UI", "[\"" + STUDY_UID + "\"]", tagToRemove);
        appendTagIfDifferent(sb, "0020000E", "UI", "[\"" + SERIES_UID + "\"]", tagToRemove);
        appendTagIfDifferent(sb, "00080018", "UI", "[\"" + SOP_UID + "\"]", tagToRemove);
        appendTagIfDifferent(sb, "00080016", "UI", "[\"" + CT_SOP_CLASS + "\"]", tagToRemove);
        appendTagIfDifferent(sb, "00280010", "US", "[512]", tagToRemove);
        appendTagIfDifferent(sb, "00280011", "US", "[512]", tagToRemove);
        appendTagIfDifferent(sb, "00280002", "US", "[1]", tagToRemove);
        appendTagIfDifferent(sb, "00280004", "CS", "[\"MONOCHROME2\"]", tagToRemove);
        appendTagIfDifferent(sb, "00280100", "US", "[16]", tagToRemove);
        appendTagIfDifferent(sb, "00280101", "US", "[16]", tagToRemove);
        appendTagIfDifferent(sb, "00280102", "US", "[15]", tagToRemove);
        appendTagIfDifferent(sb, "00280103", "US", "[0]", tagToRemove);
        appendTagIfDifferent(sb, "00281052", "DS", "[-1024]", tagToRemove);
        appendTagIfDifferent(sb, "00281053", "DS", "[1]", tagToRemove);
        // remove trailing comma
        int lastComma = sb.lastIndexOf(",");
        if (lastComma > 0) {
            sb.deleteCharAt(lastComma);
        }
        sb.append("\n  }\n]");
        return sb.toString();
    }

    private void appendTagIfDifferent(StringBuilder sb, String tag, String vr, String value, String tagToRemove) {
        if (!tag.equals(tagToRemove)) {
            sb.append("    \"").append(tag).append("\": { \"vr\": \"").append(vr).append("\", \"Value\": ").append(value).append(" },\n");
        }
    }

    private static String removeTag(String body, String tag) throws JsonProcessingException {
        JsonNode root = new ObjectMapper().readTree(body);
        ((ObjectNode) root.get(0)).remove(tag);
        return root.toString();
    }

    private static String replaceValue(String body, String tag, String valueJson)
        throws JsonProcessingException {
        ObjectMapper mapper = exactDecimalMapper();
        JsonNode root = mapper.readTree(body);
        ((ObjectNode) root.get(0).get(tag)).set("Value", mapper.readTree(valueJson));
        return root.toString();
    }

    private static String replaceVr(String body, String tag, String vr) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(body);
        ((ObjectNode) root.get(0).get(tag)).put("vr", vr);
        return root.toString();
    }

    private static String replaceValues(String body, String... tagAndValueJson) throws JsonProcessingException {
        String result = body;
        for (int i = 0; i < tagAndValueJson.length; i += 2) {
            result = replaceValue(result, tagAndValueJson[i], tagAndValueJson[i + 1]);
        }
        return result;
    }

    private static String addAttribute(String body, String tag, String vr, String valueJson)
        throws JsonProcessingException {
        ObjectMapper mapper = exactDecimalMapper();
        JsonNode root = mapper.readTree(body);
        ObjectNode attribute = mapper.createObjectNode();
        attribute.put("vr", vr);
        attribute.set("Value", mapper.readTree(valueJson));
        ((ObjectNode) root.get(0)).set(tag, attribute);
        return root.toString();
    }

    private static String addValidVoiLutSequence(String body) throws JsonProcessingException {
        return addAttribute(
            body,
            "00283010",
            "SQ",
            """
                [{
                  "00283002": { "vr": "US", "Value": [1, 0, 16] },
                  "00283003": { "vr": "LO", "Value": ["Default VOI LUT"] },
                  "00283006": { "vr": "OW", "InlineBinary": "AAA=" }
                }]
                """
        );
    }

    private static String replaceValueWithRawNumber(String body, String tag, String rawNumber)
        throws JsonProcessingException {
        ObjectMapper mapper = exactDecimalMapper();
        JsonNode root = mapper.readTree(body);
        ((ObjectNode) root.get(0).get(tag)).set(
            "Value",
            mapper.createArrayNode().addRawValue(new RawValue(rawNumber))
        );
        return mapper.writeValueAsString(root);
    }

    private static ObjectMapper exactDecimalMapper() {
        return new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .configure(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES, false);
    }
}
