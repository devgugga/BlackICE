package dev.blackice.worklist.infrastructure.dicomweb;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.blackice.worklist.application.result.StudySummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QidoStudyResponseParserTest {

    private QidoStudyResponseParser parser;

    @BeforeEach
    void setUp() {
        parser = new QidoStudyResponseParser(new ObjectMapper());
    }

    @Test
    void parses_person_name_multivalue_modalities_and_counts() {
        String body = """
            [{
              "0020000D":{"vr":"UI","Value":["1.2.840.1"]},
              "00100010":{"vr":"PN","Value":[{"Alphabetic":"MARIA^SILVA"}]},
              "00100020":{"vr":"LO","Value":["123"]},
              "00100021":{"vr":"LO","Value":["HOSPITAL-A"]},
              "00080020":{"vr":"DA","Value":["20260822"]},
              "00080030":{"vr":"TM","Value":["103512.250"]},
              "00080061":{"vr":"CS","Value":["CT","SR"]},
              "00081030":{"vr":"LO","Value":["CT CHEST"]},
              "00201206":{"vr":"IS","Value":[3]},
              "00201208":{"vr":"IS","Value":[187]}
            }]
            """;

        List<StudySummary> results = parser.parse(body);
        assertEquals(1, results.size());

        StudySummary study = results.getFirst();
        assertEquals("1.2.840.1", study.studyInstanceUid());
        assertEquals("MARIA^SILVA", study.patientName());
        assertEquals("123", study.patientId());
        assertEquals("HOSPITAL-A", study.patientIdIssuer());
        assertEquals("2026-08-22", study.studyDate());
        assertEquals("10:35:12.250", study.studyTime());
        assertEquals(List.of("CT", "SR"), study.modalities());
        assertEquals("CT CHEST", study.description());
        assertEquals(3, study.seriesCount());
        assertEquals(187, study.instanceCount());
    }

    @Test
    void rejects_missing_uid_wrong_vr_and_invalid_count() {
        assertThrows(IllegalArgumentException.class,
            () -> parser.parse("[{\"00100010\":{\"vr\":\"PN\",\"Value\":[]}}]"));
        assertThrows(IllegalArgumentException.class,
            () -> parser.parse("[{\"0020000D\":{\"vr\":\"LO\",\"Value\":[\"1.2.3\"]}}]"));
        assertThrows(IllegalArgumentException.class,
            () -> parser.parse("[{\"0020000D\":{\"vr\":\"UI\",\"Value\":[\"1.2.3\"]},\"00201206\":{\"vr\":\"IS\",\"Value\":[\"many\"]}}]"));
    }

    @Test
    void returns_empty_list_for_empty_array_null_or_blank_body() {
        assertTrue(parser.parse(null).isEmpty());
        assertTrue(parser.parse("").isEmpty());
        assertTrue(parser.parse("   ").isEmpty());
        assertTrue(parser.parse("[]").isEmpty());
    }

    @Test
    void parses_minimal_study_with_all_optional_fields_missing() {
        String body = """
            [{
              "0020000D":{"vr":"UI","Value":["1.2.840.12345.1"]}
            }]
            """;

        List<StudySummary> results = parser.parse(body);
        assertEquals(1, results.size());

        StudySummary study = results.getFirst();
        assertEquals("1.2.840.12345.1", study.studyInstanceUid());
        assertNull(study.patientName());
        assertNull(study.patientId());
        assertNull(study.patientIdIssuer());
        assertNull(study.studyDate());
        assertNull(study.studyTime());
        assertEquals(List.of(), study.modalities());
        assertNull(study.description());
        assertNull(study.seriesCount());
        assertNull(study.instanceCount());
    }

    @Test
    void falls_back_to_ideographic_and_phonetic_when_alphabetic_name_is_absent() {
        String ideographicBody = """
            [{
              "0020000D":{"vr":"UI","Value":["1.2.3.4"]},
              "00100010":{"vr":"PN","Value":[{"Ideographic":"山田^太郎"}]}
            }]
            """;
        StudySummary ideoStudy = parser.parse(ideographicBody).getFirst();
        assertEquals("山田^太郎", ideoStudy.patientName());

        String phoneticBody = """
            [{
              "0020000D":{"vr":"UI","Value":["1.2.3.4"]},
              "00100010":{"vr":"PN","Value":[{"Phonetic":"やまだ^たろう"}]}
            }]
            """;
        StudySummary phoneticStudy = parser.parse(phoneticBody).getFirst();
        assertEquals("やまだ^たろう", phoneticStudy.patientName());

        String plainStringBody = """
            [{
              "0020000D":{"vr":"UI","Value":["1.2.3.4"]},
              "00100010":{"vr":"PN","Value":["DOE^JOHN"]}
            }]
            """;
        StudySummary plainStudy = parser.parse(plainStringBody).getFirst();
        assertEquals("DOE^JOHN", plainStudy.patientName());
    }

    @Test
    void parses_counts_encoded_as_strings() {
        String body = """
            [{
              "0020000D":{"vr":"UI","Value":["1.2.3.4"]},
              "00201206":{"vr":"IS","Value":["5"]},
              "00201208":{"vr":"IS","Value":["42"]}
            }]
            """;

        StudySummary study = parser.parse(body).getFirst();
        assertEquals(5, study.seriesCount());
        assertEquals(42, study.instanceCount());
    }

    @Test
    void parses_various_dicom_tm_time_precisions() {
        String hhmmss = """
            [{"0020000D":{"vr":"UI","Value":["1.2.3.4"]},"00080030":{"vr":"TM","Value":["143015"]}}]
            """;
        assertEquals("14:30:15", parser.parse(hhmmss).getFirst().studyTime());

        String hhmm = """
            [{"0020000D":{"vr":"UI","Value":["1.2.3.4"]},"00080030":{"vr":"TM","Value":["1430"]}}]
            """;
        assertEquals("14:30", parser.parse(hhmm).getFirst().studyTime());

        String hh = """
            [{"0020000D":{"vr":"UI","Value":["1.2.3.4"]},"00080030":{"vr":"TM","Value":["14"]}}]
            """;
        assertEquals("14", parser.parse(hh).getFirst().studyTime());

        String withMicroseconds = """
            [{"0020000D":{"vr":"UI","Value":["1.2.3.4"]},"00080030":{"vr":"TM","Value":["081200.123456"]}}]
            """;
        assertEquals("08:12:00.123456", parser.parse(withMicroseconds).getFirst().studyTime());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "[{\"0020000D\":{\"vr\":\"UI\",\"Value\":[\"invalid-uid\"]}}]",
        "[{\"0020000D\":{\"vr\":\"UI\",\"Value\":[\"1.2.3.\"]}}]",
        "[{\"0020000D\":{\"vr\":\"UI\",\"Value\":[\".1.2.3\"]}}]",
        "[{\"0020000D\":{\"vr\":\"UI\",\"Value\":[\"1..2.3\"]}}]",
        "[{\"0020000D\":{\"vr\":\"UI\",\"Value\":[\"123456\"]}}]",
        "[{\"0020000D\":{\"vr\":\"UI\",\"Value\":[\"1.2.840.113619.2.55.3.2831155964.678.1234567890123456789012345678901234567890\"]}}]"
    })
    void rejects_invalid_study_instance_uids(String json) {
        assertThrows(IllegalArgumentException.class, () -> parser.parse(json));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"0020000D\":{\"vr\":\"UI\",\"Value\":[\"1.2.3.4\"]}}",
        "\"not-an-array\"",
        "123",
        "true",
        "[123]",
        "[{}]",
        "not-valid-json"
    })
    void rejects_invalid_root_structures(String invalidJson) {
        assertThrows(IllegalArgumentException.class, () -> parser.parse(invalidJson));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "[{\"0020000D\":{\"vr\":\"UI\",\"Value\":[\"1.2.3.4\"]},\"00080020\":{\"vr\":\"DA\",\"Value\":[\"20261301\"]}}]",
        "[{\"0020000D\":{\"vr\":\"UI\",\"Value\":[\"1.2.3.4\"]},\"00080020\":{\"vr\":\"DA\",\"Value\":[\"invalid-date\"]}}]",
        "[{\"0020000D\":{\"vr\":\"UI\",\"Value\":[\"1.2.3.4\"]},\"00080030\":{\"vr\":\"TM\",\"Value\":[\"250000\"]}}]",
        "[{\"0020000D\":{\"vr\":\"UI\",\"Value\":[\"1.2.3.4\"]},\"00080030\":{\"vr\":\"TM\",\"Value\":[\"126500\"]}}]",
        "[{\"0020000D\":{\"vr\":\"UI\",\"Value\":[\"1.2.3.4\"]},\"00080030\":{\"vr\":\"TM\",\"Value\":[\"120065\"]}}]",
        "[{\"0020000D\":{\"vr\":\"UI\",\"Value\":[\"1.2.3.4\"]},\"00080030\":{\"vr\":\"TM\",\"Value\":[\"invalid-time\"]}}]"
    })
    void rejects_malformed_dates_and_times(String json) {
        assertThrows(IllegalArgumentException.class, () -> parser.parse(json));
    }
}
