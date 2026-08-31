package dev.blackice.viewer.infrastructure.dicomweb;

import dev.blackice.viewer.application.result.InstanceIdentityMetadata;
import dev.blackice.viewer.application.result.SeriesMetadata;
import dev.blackice.viewer.application.result.StudyMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QidoViewerResponseParserTest {

    private final QidoViewerResponseParser parser = new QidoViewerResponseParser();

    // ==========================================
    // Study Parsing Tests
    // ==========================================

    @Test
    void parses_complete_study_metadata_correctly() {
        String json = """
            [
              {
                "0020000D": { "vr": "UI", "Value": ["1.2.840.113619.2.55.3.604688416.741.100"] },
                "00100010": { "vr": "PN", "Value": [{ "Alphabetic": "DOE^JOHN^^^" }] },
                "00100020": { "vr": "LO", "Value": ["PAT-12345"] },
                "00100021": { "vr": "LO", "Value": ["HOSPITAL-A"] },
                "00080020": { "vr": "DA", "Value": ["20260824"] },
                "00080030": { "vr": "TM", "Value": ["143000"] },
                "00081030": { "vr": "LO", "Value": ["CHEST CT WITH CONTRAST"] }
              }
            ]
            """;

        StudyMetadata study = parser.parseStudy(json);

        assertNotNull(study);
        assertEquals("1.2.840.113619.2.55.3.604688416.741.100", study.studyInstanceUid());
        assertEquals("DOE^JOHN^^^", study.patientName());
        assertEquals("PAT-12345", study.patientId());
        assertEquals("HOSPITAL-A", study.patientIdIssuer());
        assertEquals("2026-08-24", study.studyDate());
        assertEquals("14:30:00", study.studyTime());
        assertEquals("CHEST CT WITH CONTRAST", study.description());
    }

    @Test
    void parses_study_with_plain_string_person_name_and_partial_time() {
        String json = """
            [
              {
                "0020000D": { "vr": "UI", "Value": ["1.2.840.113619.2.55.3.100"] },
                "00100010": { "vr": "PN", "Value": ["DOE^JANE"] },
                "00080030": { "vr": "TM", "Value": ["1430"] }
              }
            ]
            """;

        StudyMetadata study = parser.parseStudy(json);

        assertNotNull(study);
        assertEquals("DOE^JANE", study.patientName());
        assertEquals("14:30", study.studyTime());
        assertNull(study.patientId());
        assertNull(study.studyDate());
        assertNull(study.description());
    }

    @Test
    void parse_study_returns_null_on_empty_array() {
        assertNull(parser.parseStudy("[]"));
    }

    @Test
    void rejects_study_query_with_more_than_one_match() {
        String json = """
            [
              { "0020000D": { "vr": "UI", "Value": ["1.2.3.1"] } },
              { "0020000D": { "vr": "UI", "Value": ["1.2.3.2"] } }
            ]
            """;

        QidoViewerResponseParser.InvalidResponseException ex = assertThrows(
            QidoViewerResponseParser.InvalidResponseException.class,
            () -> parser.parseStudy(json)
        );
        assertFalse(ex.getMessage().contains("1.2.3"));
    }

    @Test
    void rejects_study_with_missing_or_invalid_study_uid() {
        String missingUid = """
            [
              { "00100010": { "vr": "PN", "Value": ["DOE^JOHN"] } }
            ]
            """;
        assertThrows(QidoViewerResponseParser.InvalidResponseException.class, () -> parser.parseStudy(missingUid));

        String invalidUid = """
            [
              { "0020000D": { "vr": "UI", "Value": ["1.02.34"] } }
            ]
            """;
        assertThrows(QidoViewerResponseParser.InvalidResponseException.class, () -> parser.parseStudy(invalidUid));
    }

    @ParameterizedTest
    @ValueSource(strings = {" 1.2.3", "1.2.3 ", "\t1.2.3"})
    void rejects_uid_that_would_require_normalization(String uid) {
        String json = "[{\"0020000D\":{\"vr\":\"UI\",\"Value\":[\"" + uid + "\"]}}]";
        QidoViewerResponseParser.InvalidResponseException error = assertThrows(
            QidoViewerResponseParser.InvalidResponseException.class,
            () -> parser.parseStudy(json)
        );
        assertFalse(error.getMessage().contains("1.2.3"));
    }

    @Test
    void rejects_uid_with_vm_greater_than_one() {
        String json = "[{\"0020000D\":{\"vr\":\"UI\",\"Value\":[\"1.2.3\",\"1.2.4\"]}}]";
        assertThrows(QidoViewerResponseParser.InvalidResponseException.class, () -> parser.parseStudy(json));
    }

    @Test
    void rejects_study_with_invalid_da_or_tm_format() {
        String invalidDa = """
            [
              {
                "0020000D": { "vr": "UI", "Value": ["1.2.3"] },
                "00080020": { "vr": "DA", "Value": ["2026-08-24"] }
              }
            ]
            """;
        assertThrows(QidoViewerResponseParser.InvalidResponseException.class, () -> parser.parseStudy(invalidDa));

        String invalidTm = """
            [
              {
                "0020000D": { "vr": "UI", "Value": ["1.2.3"] },
                "00080030": { "vr": "TM", "Value": ["259999"] }
              }
            ]
            """;
        assertThrows(QidoViewerResponseParser.InvalidResponseException.class, () -> parser.parseStudy(invalidTm));
    }

    // ==========================================
    // Series Parsing Tests
    // ==========================================

    @Test
    void parses_series_list_with_all_attributes() {
        String json = """
            [
              {
                "0020000E": { "vr": "UI", "Value": ["1.2.3.1"] },
                "00200011": { "vr": "IS", "Value": [1] },
                "00080060": { "vr": "CS", "Value": ["CT"] },
                "0008103E": { "vr": "LO", "Value": ["AXIAL RECON"] },
                "00201209": { "vr": "IS", "Value": [120] }
              },
              {
                "0020000E": { "vr": "UI", "Value": ["1.2.3.2"] },
                "00200011": { "vr": "IS", "Value": ["2"] },
                "00080060": { "vr": "CS", "Value": ["CT"] },
                "0008103E": { "vr": "LO", "Value": ["CORONAL RECON"] },
                "00201209": { "vr": "IS", "Value": ["80"] }
              }
            ]
            """;

        List<SeriesMetadata> series = parser.parseSeries(json);

        assertEquals(2, series.size());
        assertEquals("1.2.3.1", series.get(0).seriesInstanceUid());
        assertEquals(1, series.get(0).seriesNumber());
        assertEquals("CT", series.get(0).modality());
        assertEquals("AXIAL RECON", series.get(0).description());
        assertEquals(120, series.get(0).instanceCount());

        assertEquals("1.2.3.2", series.get(1).seriesInstanceUid());
        assertEquals(2, series.get(1).seriesNumber());
    }

    @Test
    void parses_series_list_with_missing_optional_attributes() {
        String json = """
            [
              {
                "0020000E": { "vr": "UI", "Value": ["1.2.3.1"] }
              }
            ]
            """;

        List<SeriesMetadata> series = parser.parseSeries(json);

        assertEquals(1, series.size());
        assertEquals("1.2.3.1", series.get(0).seriesInstanceUid());
        assertNull(series.get(0).seriesNumber());
        assertNull(series.get(0).modality());
        assertNull(series.get(0).description());
        assertNull(series.get(0).instanceCount());
    }

    @Test
    void parse_series_returns_empty_list_on_empty_array() {
        List<SeriesMetadata> series = parser.parseSeries("[]");
        assertNotNull(series);
        assertTrue(series.isEmpty());
    }

    @Test
    void rejects_duplicate_series_instance_uid_in_series_list() {
        String json = """
            [
              { "0020000E": { "vr": "UI", "Value": ["1.2.3.1"] } },
              { "0020000E": { "vr": "UI", "Value": ["1.2.3.1"] } }
            ]
            """;

        QidoViewerResponseParser.InvalidResponseException ex = assertThrows(
            QidoViewerResponseParser.InvalidResponseException.class,
            () -> parser.parseSeries(json)
        );
        assertFalse(ex.getMessage().contains("1.2.3.1"));
    }

    @Test
    void rejects_invalid_series_instance_uid() {
        String json = """
            [
              { "0020000E": { "vr": "UI", "Value": ["invalid..uid"] } }
            ]
            """;
        assertThrows(QidoViewerResponseParser.InvalidResponseException.class, () -> parser.parseSeries(json));
    }

    // ==========================================
    // Instances Parsing Tests
    // ==========================================

    @Test
    void parses_instances_with_present_and_absent_number_of_frames() {
        String json = """
            [
              {
                "0020000E": { "vr": "UI", "Value": ["1.2.3.1"] },
                "00080018": { "vr": "UI", "Value": ["1.2.3.1.1"] },
                "00080016": { "vr": "UI", "Value": ["1.2.840.10008.5.1.4.1.1.2"] }
              },
              {
                "0020000E": { "vr": "UI", "Value": ["1.2.3.1"] },
                "00080018": { "vr": "UI", "Value": ["1.2.3.1.2"] },
                "00080016": { "vr": "UI", "Value": ["1.2.840.10008.5.1.4.1.1.2"] },
                "00280008": { "vr": "IS", "Value": [1] }
              },
              {
                "0020000E": { "vr": "UI", "Value": ["1.2.3.1"] },
                "00080018": { "vr": "UI", "Value": ["1.2.3.1.3"] },
                "00080016": { "vr": "UI", "Value": ["1.2.840.10008.5.1.4.1.1.2"] },
                "00280008": { "vr": "IS", "Value": ["45"] }
              }
            ]
            """;

        List<InstanceIdentityMetadata> instances = parser.parseInstances(json);

        assertEquals(3, instances.size());

        assertEquals("1.2.3.1", instances.get(0).seriesInstanceUid());
        assertEquals("1.2.3.1.1", instances.get(0).sopInstanceUid());
        assertEquals("1.2.840.10008.5.1.4.1.1.2", instances.get(0).sopClassUid());
        assertNull(instances.get(0).numberOfFrames());

        assertEquals("1.2.3.1.2", instances.get(1).sopInstanceUid());
        assertEquals(1, instances.get(1).numberOfFrames());

        assertEquals("1.2.3.1.3", instances.get(2).sopInstanceUid());
        assertEquals(45, instances.get(2).numberOfFrames());
    }

    @Test
    void parse_instances_returns_empty_list_on_empty_array() {
        List<InstanceIdentityMetadata> instances = parser.parseInstances("[]");
        assertNotNull(instances);
        assertTrue(instances.isEmpty());
    }

    @Test
    void rejects_duplicate_sop_instance_uid_in_instances_list() {
        String json = """
            [
              {
                "0020000E": { "vr": "UI", "Value": ["1.2.3.1"] },
                "00080018": { "vr": "UI", "Value": ["1.2.3.1.1"] },
                "00080016": { "vr": "UI", "Value": ["1.2.840.10008.5.1.4.1.1.2"] }
              },
              {
                "0020000E": { "vr": "UI", "Value": ["1.2.3.1"] },
                "00080018": { "vr": "UI", "Value": ["1.2.3.1.1"] },
                "00080016": { "vr": "UI", "Value": ["1.2.840.10008.5.1.4.1.1.2"] }
              }
            ]
            """;

        QidoViewerResponseParser.InvalidResponseException ex = assertThrows(
            QidoViewerResponseParser.InvalidResponseException.class,
            () -> parser.parseInstances(json)
        );
        assertFalse(ex.getMessage().contains("1.2.3.1.1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "1.5", ""})
    void rejects_malformed_number_of_frames(String invalidFrames) {
        String json = String.format("""
            [
              {
                "0020000E": { "vr": "UI", "Value": ["1.2.3.1"] },
                "00080018": { "vr": "UI", "Value": ["1.2.3.1.1"] },
                "00080016": { "vr": "UI", "Value": ["1.2.840.10008.5.1.4.1.1.2"] },
                "00280008": { "vr": "IS", "Value": ["%s"] }
              }
            ]
            """, invalidFrames);

        assertThrows(QidoViewerResponseParser.InvalidResponseException.class, () -> parser.parseInstances(json));
    }

    @Test
    void rejects_missing_required_instance_uids() {
        String missingSopUid = """
            [
              {
                "0020000E": { "vr": "UI", "Value": ["1.2.3.1"] },
                "00080016": { "vr": "UI", "Value": ["1.2.840.10008.5.1.4.1.1.2"] }
              }
            ]
            """;
        assertThrows(QidoViewerResponseParser.InvalidResponseException.class, () -> parser.parseInstances(missingSopUid));

        String missingSopClassUid = """
            [
              {
                "0020000E": { "vr": "UI", "Value": ["1.2.3.1"] },
                "00080018": { "vr": "UI", "Value": ["1.2.3.1.1"] }
              }
            ]
            """;
        assertThrows(QidoViewerResponseParser.InvalidResponseException.class, () -> parser.parseInstances(missingSopClassUid));

        String missingSeriesUid = """
            [
              {
                "00080018": { "vr": "UI", "Value": ["1.2.3.1.1"] },
                "00080016": { "vr": "UI", "Value": ["1.2.840.10008.5.1.4.1.1.2"] }
              }
            ]
            """;
        assertThrows(QidoViewerResponseParser.InvalidResponseException.class, () -> parser.parseInstances(missingSeriesUid));
    }

    @Test
    void rejects_malformed_json_and_non_array_roots() {
        assertThrows(QidoViewerResponseParser.InvalidResponseException.class, () -> parser.parseStudy("not-json"));
        assertThrows(QidoViewerResponseParser.InvalidResponseException.class, () -> parser.parseStudy("{}"));
        assertThrows(QidoViewerResponseParser.InvalidResponseException.class, () -> parser.parseStudy(null));
        assertThrows(QidoViewerResponseParser.InvalidResponseException.class, () -> parser.parseStudy("   "));

        assertThrows(QidoViewerResponseParser.InvalidResponseException.class, () -> parser.parseSeries("not-json"));
        assertThrows(QidoViewerResponseParser.InvalidResponseException.class, () -> parser.parseSeries("{}"));
        assertThrows(QidoViewerResponseParser.InvalidResponseException.class, () -> parser.parseSeries(null));

        assertThrows(QidoViewerResponseParser.InvalidResponseException.class, () -> parser.parseInstances("not-json"));
        assertThrows(QidoViewerResponseParser.InvalidResponseException.class, () -> parser.parseInstances("{}"));
        assertThrows(QidoViewerResponseParser.InvalidResponseException.class, () -> parser.parseInstances(null));
    }
}
