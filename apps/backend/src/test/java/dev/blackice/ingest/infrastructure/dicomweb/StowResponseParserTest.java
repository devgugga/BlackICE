package dev.blackice.ingest.infrastructure.dicomweb;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.blackice.ingest.application.result.StowInstanceResult;
import dev.blackice.ingest.application.result.StowStudyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    void rejected_result_is_not_overwritten_by_later_accepted_dataset() {
        String body = """
            [
              {"00081198":{"vr":"SQ","Value":[
                {"00081155":{"vr":"UI","Value":["1.2.3.1"]},
                 "00081197":{"vr":"US","Value":[272]}}
              ]}},
              {"00081199":{"vr":"SQ","Value":[
                {"00081155":{"vr":"UI","Value":["1.2.3.1"]}}
              ]}}
            ]
            """;

        StowStudyResult result = parser.parse(
            "1.2.3", body, new LinkedHashSet<>(List.of("1.2.3.1")));

        assertEquals(StowInstanceResult.Status.REJECTED, result.instances().getFirst().status());
        assertEquals(272, result.instances().getFirst().reason());
    }

    @Test
    void accepted_result_is_overwritten_by_later_rejected_dataset() {
        String body = """
            [
              {"00081199":{"vr":"SQ","Value":[
                {"00081155":{"vr":"UI","Value":["1.2.3.1"]}}
              ]}},
              {"00081198":{"vr":"SQ","Value":[
                {"00081155":{"vr":"UI","Value":["1.2.3.1"]},
                 "00081197":{"vr":"US","Value":[272]}}
              ]}}
            ]
            """;

        StowStudyResult result = parser.parse(
            "1.2.3", body, new LinkedHashSet<>(List.of("1.2.3.1")));

        assertEquals(StowInstanceResult.Status.REJECTED, result.instances().getFirst().status());
        assertEquals(272, result.instances().getFirst().reason());
    }

    @Test
    void warning_result_is_not_overwritten_by_later_accepted_dataset() {
        String body = """
            [
              {"00081199":{"vr":"SQ","Value":[
                {"00081155":{"vr":"UI","Value":["1.2.3.1"]},
                 "00081196":{"vr":"US","Value":[45056]}}
              ]}},
              {"00081199":{"vr":"SQ","Value":[
                {"00081155":{"vr":"UI","Value":["1.2.3.1"]}}
              ]}}
            ]
            """;

        StowStudyResult result = parser.parse(
            "1.2.3", body, new LinkedHashSet<>(List.of("1.2.3.1")));

        assertEquals(StowInstanceResult.Status.WARNING, result.instances().getFirst().status());
        assertEquals(45056, result.instances().getFirst().reason());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejects_null_or_blank_body(String body) {
        assertThrows(StowResponseParser.InvalidResponseException.class,
            () -> parser.parse("1.2.3", body, new LinkedHashSet<>(List.of("1.2.3.1"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "42", "true", "\"text\"", "[]", "{}", "[{}]", "[42]"})
    void rejects_roots_without_a_valid_stow_dataset(String body) {
        assertThrows(StowResponseParser.InvalidResponseException.class,
            () -> parser.parse("1.2.3", body, new LinkedHashSet<>(List.of("1.2.3.1"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"00081199\":{\"vr\":\"LO\",\"Value\":[]}}",
        "{\"00081199\":{\"vr\":\"SQ\",\"Value\":{}}}",
        "{\"00081199\":{\"vr\":\"SQ\",\"Value\":[42]}}",
        "{\"00081198\":{\"vr\":\"SQ\",\"Value\":\"invalid\"}}"
    })
    void rejects_malformed_sequence_attributes(String body) {
        assertThrows(StowResponseParser.InvalidResponseException.class,
            () -> parser.parse("1.2.3", body, new LinkedHashSet<>(List.of("1.2.3.1"))));
    }

    @Test
    void rejects_response_that_confirms_none_of_the_submitted_sops() {
        String body = "{\"00081199\":{\"vr\":\"SQ\",\"Value\":["
            + "{\"00081155\":{\"vr\":\"UI\",\"Value\":[\"9.9.9\"]}}]}}";

        assertThrows(StowResponseParser.InvalidResponseException.class,
            () -> parser.parse("1.2.3", body, new LinkedHashSet<>(List.of("1.2.3.1"))));
    }

    @Test
    void propagates_unexpected_object_mapper_runtime_failure() throws Exception {
        ObjectMapper brokenMapper = mock(ObjectMapper.class);
        when(brokenMapper.readTree(anyString())).thenThrow(new IllegalStateException("unexpected parser bug"));
        StowResponseParser brokenParser = new StowResponseParser(brokenMapper);

        assertThrows(IllegalStateException.class,
            () -> brokenParser.parse("1.2.3", "{}", new LinkedHashSet<>(List.of("1.2.3.1"))));
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

    @ParameterizedTest
    @ValueSource(strings = {
        "1.02.3",
        " 1.2.3",
        "1.2.3 ",
        "1.2 3",
        "1.222222222222222222222222222222222222222222222222222222222222222"
    })
    void rejects_invalid_referenced_sop_instance_uid(String uid) {
        String body = "{\"00081199\":{\"vr\":\"SQ\",\"Value\":["
            + "{\"00081155\":{\"vr\":\"UI\",\"Value\":[\"" + uid + "\"]}}]}}";

        assertThrows(StowResponseParser.InvalidResponseException.class,
            () -> parser.parse("1.2.3", body, new LinkedHashSet<>(List.of(uid))));
    }

    @Test
    void preserves_exact_valid_referenced_sop_instance_uid() {
        String exactUid = "1.2.840.10008.5.1.4.1.1.7.314159";
        String body = "{\"00081199\":{\"vr\":\"SQ\",\"Value\":["
            + "{\"00081155\":{\"vr\":\"UI\",\"Value\":[\"" + exactUid + "\"]}}]}}";

        StowStudyResult result = parser.parse(
            "1.2.3", body, new LinkedHashSet<>(List.of(exactUid)));

        assertEquals(exactUid, result.instances().getFirst().sopInstanceUid());
    }
}
