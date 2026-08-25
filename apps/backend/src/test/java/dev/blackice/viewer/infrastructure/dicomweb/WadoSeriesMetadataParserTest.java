package dev.blackice.viewer.infrastructure.dicomweb;

import dev.blackice.viewer.application.exception.InvalidArchiveMetadataException;
import dev.blackice.viewer.application.input.ViewerSeriesRef;
import dev.blackice.viewer.application.result.ViewerInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

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
    private static final String CT_SOP_CLASS = "1.2.840.10008.5.1.4.1.1.2";
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
    void parse_handles_string_encoded_numbers_and_multivalued_window_values() {
        String body = """
            [
              {
                "0020000D": { "vr": "UI", "Value": ["%s"] },
                "0020000E": { "vr": "UI", "Value": ["%s"] },
                "00080018": { "vr": "UI", "Value": ["%s"] },
                "00080016": { "vr": "UI", "Value": ["%s"] },
                "00200013": { "vr": "IS", "Value": [" 42 "] },
                "00280010": { "vr": "US", "Value": ["256"] },
                "00280011": { "vr": "US", "Value": ["256"] },
                "00280002": { "vr": "US", "Value": ["1"] },
                "00280004": { "vr": "CS", "Value": ["MONOCHROME1"] },
                "00280100": { "vr": "US", "Value": ["8"] },
                "00280101": { "vr": "US", "Value": ["8"] },
                "00280102": { "vr": "US", "Value": ["7"] },
                "00280103": { "vr": "US", "Value": ["0"] },
                "00281050": { "vr": "DS", "Value": ["40.0", "50.5"] },
                "00281051": { "vr": "DS", "Value": ["400.0", "350.0"] }
              }
            ]
            """.formatted(STUDY_UID, SERIES_UID, SOP_UID, CT_SOP_CLASS);

        List<ViewerInstance> instances = parser.parse(body, SERIES_REF);
        assertEquals(1, instances.size());

        ViewerInstance image = instances.getFirst();
        assertEquals(42, image.instanceNumber());
        assertEquals(256, image.rows());
        assertEquals(256, image.columns());
        assertEquals(1, image.samplesPerPixel());
        assertEquals("MONOCHROME1", image.photometricInterpretation());
        assertEquals(8, image.bitsAllocated());
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
    void parse_planar_configuration_conditional_handling() {
        // SamplesPerPixel = 3 with PlanarConfiguration = 0 is valid
        String validColorBody = """
            [
              {
                "0020000D": { "vr": "UI", "Value": ["%s"] },
                "0020000E": { "vr": "UI", "Value": ["%s"] },
                "00080018": { "vr": "UI", "Value": ["%s"] },
                "00080016": { "vr": "UI", "Value": ["%s"] },
                "00280010": { "vr": "US", "Value": [512] },
                "00280011": { "vr": "US", "Value": [512] },
                "00280002": { "vr": "US", "Value": [3] },
                "00280004": { "vr": "CS", "Value": ["RGB"] },
                "00280006": { "vr": "US", "Value": [0] },
                "00280100": { "vr": "US", "Value": [8] },
                "00280101": { "vr": "US", "Value": [8] },
                "00280102": { "vr": "US", "Value": [7] },
                "00280103": { "vr": "US", "Value": [0] }
              }
            ]
            """.formatted(STUDY_UID, SERIES_UID, SOP_UID, CT_SOP_CLASS);

        List<ViewerInstance> instances = parser.parse(validColorBody, SERIES_REF);
        assertEquals(1, instances.size());
        assertEquals(0, instances.getFirst().planarConfiguration());
        assertEquals(3, instances.getFirst().samplesPerPixel());

        // SamplesPerPixel = 3 with missing PlanarConfiguration throws
        String invalidColorBody = """
            [
              {
                "0020000D": { "vr": "UI", "Value": ["%s"] },
                "0020000E": { "vr": "UI", "Value": ["%s"] },
                "00080018": { "vr": "UI", "Value": ["%s"] },
                "00080016": { "vr": "UI", "Value": ["%s"] },
                "00280010": { "vr": "US", "Value": [512] },
                "00280011": { "vr": "US", "Value": [512] },
                "00280002": { "vr": "US", "Value": [3] },
                "00280004": { "vr": "CS", "Value": ["RGB"] },
                "00280100": { "vr": "US", "Value": [8] },
                "00280101": { "vr": "US", "Value": [8] },
                "00280102": { "vr": "US", "Value": [7] },
                "00280103": { "vr": "US", "Value": [0] }
              }
            ]
            """.formatted(STUDY_UID, SERIES_UID, SOP_UID, CT_SOP_CLASS);

        assertThrows(InvalidArchiveMetadataException.class, () -> parser.parse(invalidColorBody, SERIES_REF));
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

    private String baseDatasetJson(String sopClassUid, String sopInstanceUid) {
        return baseDatasetJson(sopClassUid, sopInstanceUid, STUDY_UID, SERIES_UID);
    }

    private String baseDatasetJson(String sopClassUid, String sopInstanceUid, String studyUid, String seriesUid) {
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
                "00280103": { "vr": "US", "Value": [0] }
              }
            ]
            """.formatted(studyUid, seriesUid, sopInstanceUid, sopClassUid);
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
}
