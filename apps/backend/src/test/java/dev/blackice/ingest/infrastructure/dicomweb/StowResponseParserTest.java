package dev.blackice.ingest.infrastructure.dicomweb;

import dev.blackice.ingest.application.result.StowInstanceResult;
import dev.blackice.ingest.application.result.StowStudyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StowResponseParserTest {

    private StowResponseParser parser;

    @BeforeEach
    void setUp() {
        parser = new StowResponseParser();
    }

    @Test
    void archive_omitted_uid_is_never_reported_as_success() {
        String body = """
            {"00081199":{"vr":"SQ","Value":[
              {"00081155":{"vr":"UI","Value":["1.2.3.1"]}}
            ]}}
            """;
        StowStudyResult result = parser.parse(
            "1.2.3", body, new LinkedHashSet<>(List.of("1.2.3.1", "1.2.3.2")));

        assertEquals("1.2.3", result.studyInstanceUid());
        assertEquals(2, result.instances().size());

        assertEquals("1.2.3.1", result.instances().get(0).sopInstanceUid());
        assertEquals(StowInstanceResult.Status.ACCEPTED, result.instances().get(0).status());
        assertNull(result.instances().get(0).reason());

        assertEquals("1.2.3.2", result.instances().get(1).sopInstanceUid());
        assertEquals(StowInstanceResult.Status.UNCONFIRMED, result.instances().get(1).status());
        assertNull(result.instances().get(1).reason());
    }

    @Test
    void parses_success_failure_warning_and_unconfirmed_statuses() {
        String body = """
            {
              "00081199": {
                "vr": "SQ",
                "Value": [
                  {
                    "00081155": {"vr": "UI", "Value": ["1.2.3.1"]}
                  },
                  {
                    "00081155": {"vr": "UI", "Value": ["1.2.3.2"]},
                    "00081196": {"vr": "US", "Value": [45056]}
                  }
                ]
              },
              "00081198": {
                "vr": "SQ",
                "Value": [
                  {
                    "00081155": {"vr": "UI", "Value": ["1.2.3.3"]},
                    "00081197": {"vr": "US", "Value": [272]}
                  }
                ]
              }
            }
            """;

        StowStudyResult result = parser.parse(
            "1.2.3",
            body,
            new LinkedHashSet<>(List.of("1.2.3.1", "1.2.3.2", "1.2.3.3", "1.2.3.4"))
        );

        assertEquals(4, result.instances().size());

        assertEquals("1.2.3.1", result.instances().get(0).sopInstanceUid());
        assertEquals(StowInstanceResult.Status.ACCEPTED, result.instances().get(0).status());
        assertNull(result.instances().get(0).reason());

        assertEquals("1.2.3.2", result.instances().get(1).sopInstanceUid());
        assertEquals(StowInstanceResult.Status.WARNING, result.instances().get(1).status());
        assertEquals(45056, result.instances().get(1).reason());

        assertEquals("1.2.3.3", result.instances().get(2).sopInstanceUid());
        assertEquals(StowInstanceResult.Status.REJECTED, result.instances().get(2).status());
        assertEquals(272, result.instances().get(2).reason());

        assertEquals("1.2.3.4", result.instances().get(3).sopInstanceUid());
        assertEquals(StowInstanceResult.Status.UNCONFIRMED, result.instances().get(3).status());
        assertNull(result.instances().get(3).reason());
    }

    @Test
    void sop_in_both_sequences_prefers_rejection() {
        String body = """
            {
              "00081199": {
                "vr": "SQ",
                "Value": [
                  {"00081155": {"vr": "UI", "Value": ["1.2.3.1"]}}
                ]
              },
              "00081198": {
                "vr": "SQ",
                "Value": [
                  {
                    "00081155": {"vr": "UI", "Value": ["1.2.3.1"]},
                    "00081197": {"vr": "US", "Value": [272]}
                  }
                ]
              }
            }
            """;

        StowStudyResult result = parser.parse(
            "1.2.3", body, new LinkedHashSet<>(List.of("1.2.3.1")));

        assertEquals(1, result.instances().size());
        assertEquals("1.2.3.1", result.instances().get(0).sopInstanceUid());
        assertEquals(StowInstanceResult.Status.REJECTED, result.instances().get(0).status());
        assertEquals(272, result.instances().get(0).reason());
    }

    @Test
    void empty_body_or_missing_sequences_marks_all_instances_unconfirmed() {
        StowStudyResult fromEmptyObject = parser.parse(
            "1.2.3", "{}", new LinkedHashSet<>(List.of("1.2.3.1", "1.2.3.2")));
        assertEquals(StowInstanceResult.Status.UNCONFIRMED, fromEmptyObject.instances().get(0).status());
        assertEquals(StowInstanceResult.Status.UNCONFIRMED, fromEmptyObject.instances().get(1).status());

        StowStudyResult fromBlank = parser.parse(
            "1.2.3", "   ", new LinkedHashSet<>(List.of("1.2.3.1")));
        assertEquals(StowInstanceResult.Status.UNCONFIRMED, fromBlank.instances().get(0).status());

        StowStudyResult fromNull = parser.parse(
            "1.2.3", null, new LinkedHashSet<>(List.of("1.2.3.1")));
        assertEquals(StowInstanceResult.Status.UNCONFIRMED, fromNull.instances().get(0).status());
    }

    @Test
    void supports_json_array_response() {
        String body = """
            [
              {
                "00081199": {
                  "vr": "SQ",
                  "Value": [
                    {"00081155": {"vr": "UI", "Value": ["1.2.3.1"]}}
                  ]
                }
              }
            ]
            """;

        StowStudyResult result = parser.parse(
            "1.2.3", body, new LinkedHashSet<>(List.of("1.2.3.1")));

        assertEquals(1, result.instances().size());
        assertEquals(StowInstanceResult.Status.ACCEPTED, result.instances().get(0).status());
    }

    @Test
    void preserves_exact_order_of_submitted_uids() {
        String body = """
            {
              "00081199": {
                "vr": "SQ",
                "Value": [
                  {"00081155": {"vr": "UI", "Value": ["1.2.3.1"]}},
                  {"00081155": {"vr": "UI", "Value": ["1.2.3.2"]}}
                ]
              }
            }
            """;

        // Submit in reverse order to prove the parser retains the caller's ordering.
        StowStudyResult result = parser.parse(
            "1.2.3", body, new LinkedHashSet<>(List.of("1.2.3.2", "1.2.3.1")));

        assertEquals("1.2.3.2", result.instances().get(0).sopInstanceUid());
        assertEquals("1.2.3.1", result.instances().get(1).sopInstanceUid());
    }
}
