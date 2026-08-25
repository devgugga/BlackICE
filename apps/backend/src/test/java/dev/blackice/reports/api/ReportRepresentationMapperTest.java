package dev.blackice.reports.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.blackice.reports.application.exception.InvalidReportRequestException;
import dev.blackice.reports.application.result.StudyReportResult;
import dev.blackice.reports.domain.ReportStatus;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportRepresentationMapperTest {

    private final ReportRepresentationMapper mapper = new ReportRepresentationMapper();
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final String STUDY_UID = "1.2.840.113619.2.55.3";
    private static final String DISPLAY_NAME = "Dr. House";
    private static final Instant T0 = Instant.parse("2026-08-25T15:00:00Z");
    private static final Instant T1 = Instant.parse("2026-08-25T15:03:00Z");
    private static final Instant T2 = Instant.parse("2026-08-25T15:05:00Z");

    @Test
    void response_record_has_exactly_eight_public_fields() {
        RecordComponent[] components = ReportResponse.class.getRecordComponents();
        assertNotNull(components);
        assertEquals(8, components.length, "ReportResponse must have exactly 8 record components");

        Set<String> fieldNames = Arrays.stream(components)
            .map(RecordComponent::getName)
            .collect(Collectors.toSet());

        Set<String> expectedFields = Set.of(
            "studyInstanceUid",
            "authorDisplayName",
            "status",
            "content",
            "editable",
            "createdAt",
            "updatedAt",
            "finalizedAt"
        );

        assertEquals(expectedFields, fieldNames);
        assertFalse(fieldNames.contains("id"), "Internal surrogate id must not be present");
        assertFalse(fieldNames.contains("authorId"), "Author subject id must not be present");
        assertFalse(fieldNames.contains("version"), "Raw version must not be present in response JSON");
    }

    @Test
    void maps_draft_study_report_result_to_response() {
        StudyReportResult result = new StudyReportResult(
            STUDY_UID,
            DISPLAY_NAME,
            ReportStatus.DRAFT,
            "Draft findings.",
            true,
            T0,
            T1,
            null,
            0L
        );

        ReportResponse response = mapper.toResponse(result);

        assertEquals(STUDY_UID, response.studyInstanceUid());
        assertEquals(DISPLAY_NAME, response.authorDisplayName());
        assertEquals(ReportStatus.DRAFT, response.status());
        assertEquals("Draft findings.", response.content());
        assertTrue(response.editable());
        assertEquals(T0, response.createdAt());
        assertEquals(T1, response.updatedAt());
        assertNull(response.finalizedAt());
    }

    @Test
    void maps_final_study_report_result_to_response() {
        StudyReportResult result = new StudyReportResult(
            STUDY_UID,
            DISPLAY_NAME,
            ReportStatus.FINAL,
            "Final conclusion.",
            false,
            T0,
            T1,
            T2,
            3L
        );

        ReportResponse response = mapper.toResponse(result);

        assertEquals(STUDY_UID, response.studyInstanceUid());
        assertEquals(DISPLAY_NAME, response.authorDisplayName());
        assertEquals(ReportStatus.FINAL, response.status());
        assertEquals("Final conclusion.", response.content());
        assertFalse(response.editable());
        assertEquals(T0, response.createdAt());
        assertEquals(T1, response.updatedAt());
        assertEquals(T2, response.finalizedAt());
    }

    @Test
    void serializes_draft_response_to_json_with_explicit_finalized_at_null() throws Exception {
        ReportResponse response = new ReportResponse(
            STUDY_UID,
            DISPLAY_NAME,
            ReportStatus.DRAFT,
            "Draft text",
            true,
            T0,
            T1,
            null
        );

        String json = objectMapper.writeValueAsString(response);
        JsonNode node = objectMapper.readTree(json);

        assertEquals(STUDY_UID, node.get("studyInstanceUid").asText());
        assertEquals(DISPLAY_NAME, node.get("authorDisplayName").asText());
        assertEquals("DRAFT", node.get("status").asText());
        assertEquals("Draft text", node.get("content").asText());
        assertTrue(node.get("editable").asBoolean());
        assertNotNull(node.get("createdAt"));
        assertNotNull(node.get("updatedAt"));
        assertTrue(node.has("finalizedAt"));
        assertTrue(node.get("finalizedAt").isNull());
        assertNull(node.get("id"));
        assertNull(node.get("authorId"));
        assertNull(node.get("version"));
    }

    @Test
    void throws_invalid_report_request_exception_on_null_result() {
        assertThrows(InvalidReportRequestException.class, () -> mapper.toResponse(null));
    }
}
