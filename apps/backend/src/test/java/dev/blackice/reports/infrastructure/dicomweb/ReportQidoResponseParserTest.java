package dev.blackice.reports.infrastructure.dicomweb;

import dev.blackice.reports.application.exception.ArchiveStudyLookupException;
import dev.blackice.reports.application.input.ReportStudyRef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportQidoResponseParserTest {

    private static final String STUDY_UID = "1.2.840.113619.2.55.3.604688416.741.100";
    private static final ReportStudyRef STUDY_REF = new ReportStudyRef(STUDY_UID);

    private static final String VALID_BODY = """
        [
          {
            "0020000D": {
              "vr": "UI",
              "Value": ["1.2.840.113619.2.55.3.604688416.741.100"]
            }
          }
        ]
        """;

    private final ReportQidoResponseParser parser = new ReportQidoResponseParser();

    @Test
    void returns_true_for_valid_matching_study_response() {
        boolean exists = parser.parse(VALID_BODY, STUDY_REF);
        assertTrue(exists);
    }

    @Test
    void returns_false_for_empty_json_array() {
        boolean exists = parser.parse("[]", STUDY_REF);
        assertFalse(exists);
    }

    @Test
    void throws_invalid_response_when_uid_mismatches() {
        String mismatchedBody = """
            [
              {
                "0020000D": {
                  "vr": "UI",
                  "Value": ["1.2.840.113619.2.55.3.604688416.741.999"]
                }
              }
            ]
            """;

        ArchiveStudyLookupException ex = assertThrows(
            ArchiveStudyLookupException.class,
            () -> parser.parse(mismatchedBody, STUDY_REF)
        );

        assertEquals(ArchiveStudyLookupException.Reason.ARCHIVE_INVALID_RESPONSE, ex.reason());
        assertEquals("ARCHIVE_INVALID_RESPONSE", ex.getMessage());
    }

    @Test
    void throws_invalid_response_when_tag_is_missing() {
        String bodyWithoutTag = """
            [
              {
                "00100010": {
                  "vr": "PN",
                  "Value": ["DOE^JOHN"]
                }
              }
            ]
            """;

        ArchiveStudyLookupException ex = assertThrows(
            ArchiveStudyLookupException.class,
            () -> parser.parse(bodyWithoutTag, STUDY_REF)
        );

        assertEquals(ArchiveStudyLookupException.Reason.ARCHIVE_INVALID_RESPONSE, ex.reason());
    }

    @Test
    void throws_invalid_response_when_vr_is_wrong_or_missing() {
        String wrongVrBody = """
            [
              {
                "0020000D": {
                  "vr": "CS",
                  "Value": ["1.2.840.113619.2.55.3.604688416.741.100"]
                }
              }
            ]
            """;

        ArchiveStudyLookupException ex = assertThrows(
            ArchiveStudyLookupException.class,
            () -> parser.parse(wrongVrBody, STUDY_REF)
        );

        assertEquals(ArchiveStudyLookupException.Reason.ARCHIVE_INVALID_RESPONSE, ex.reason());
    }

    @Test
    void throws_invalid_response_when_value_array_is_missing_or_empty() {
        String emptyValueBody = """
            [
              {
                "0020000D": {
                  "vr": "UI",
                  "Value": []
                }
              }
            ]
            """;

        ArchiveStudyLookupException ex = assertThrows(
            ArchiveStudyLookupException.class,
            () -> parser.parse(emptyValueBody, STUDY_REF)
        );

        assertEquals(ArchiveStudyLookupException.Reason.ARCHIVE_INVALID_RESPONSE, ex.reason());
    }

    @Test
    void throws_invalid_response_when_multiple_items_returned() {
        String multiItemBody = """
            [
              {
                "0020000D": {
                  "vr": "UI",
                  "Value": ["1.2.840.113619.2.55.3.604688416.741.100"]
                }
              },
              {
                "0020000D": {
                  "vr": "UI",
                  "Value": ["1.2.840.113619.2.55.3.604688416.741.101"]
                }
              }
            ]
            """;

        ArchiveStudyLookupException ex = assertThrows(
            ArchiveStudyLookupException.class,
            () -> parser.parse(multiItemBody, STUDY_REF)
        );

        assertEquals(ArchiveStudyLookupException.Reason.ARCHIVE_INVALID_RESPONSE, ex.reason());
    }

    @Test
    void throws_invalid_response_when_json_is_not_array() {
        String objectBody = """
            {
              "0020000D": {
                "vr": "UI",
                "Value": ["1.2.840.113619.2.55.3.604688416.741.100"]
              }
            }
            """;

        ArchiveStudyLookupException ex = assertThrows(
            ArchiveStudyLookupException.class,
            () -> parser.parse(objectBody, STUDY_REF)
        );

        assertEquals(ArchiveStudyLookupException.Reason.ARCHIVE_INVALID_RESPONSE, ex.reason());
    }

    @Test
    void throws_invalid_response_for_malformed_or_blank_input() {
        assertThrows(
            ArchiveStudyLookupException.class,
            () -> parser.parse("corrupted-json-content", STUDY_REF)
        );
        assertThrows(
            ArchiveStudyLookupException.class,
            () -> parser.parse("", STUDY_REF)
        );
        assertThrows(
            ArchiveStudyLookupException.class,
            () -> parser.parse(null, STUDY_REF)
        );
    }

    @Test
    void null_guard_for_expected_study() {
        assertThrows(NullPointerException.class, () -> parser.parse(VALID_BODY, (ReportStudyRef) null));
    }
}
