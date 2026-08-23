package dev.blackice.shared.api.problem;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.blackice.shared.api.problem.generated.ProblemExtensions;
import dev.blackice.shared.api.problem.generated.ProblemType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiProblemFactoryTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    private final ApiProblemFactory factory = new ApiProblemFactory(new FixedTraceContext(TRACE_ID));
    private final ObjectMapper mapper = new ObjectMapper();

    /** TraceContext com valores conhecidos: o teste não depende de um span ativo. */
    private static final class FixedTraceContext extends TraceContext {

        private final String traceId;

        private FixedTraceContext(String traceId) {
            this.traceId = traceId;
        }

        @Override
        public String traceId() {
            return traceId;
        }

        @Override
        public String spanId() {
            return "00f067aa0ba902b7";
        }
    }

    @Test
    void catalogued_type_becomes_a_problem_with_catalog_text_and_active_trace_id() {
        ApiProblem problem = factory.create(ProblemType.API_ARCHIVE_UNAVAILABLE, ProblemExtensions.none());

        assertEquals(ProblemType.API_ARCHIVE_UNAVAILABLE.type(), problem.type());
        assertEquals("API_ARCHIVE_UNAVAILABLE", problem.code());
        assertEquals(503, problem.status());
        assertEquals("Archive unavailable", problem.title());
        assertEquals("The imaging archive is temporarily unavailable.", problem.detail());
        assertEquals(TRACE_ID, problem.traceId());
        assertTrue(problem.extensions().isEmpty());
    }

    @Test
    void dicom_validation_extension_is_exposed_without_any_filename() {
        ApiProblem problem = factory.create(
            ProblemType.API_DICOM_VALIDATION_FAILED,
            new ProblemExtensions.DicomValidationViolations(List.of(
                new ProblemExtensions.Violation(0, "MALFORMED_DICOM", "The file is not valid DICOM.")
            ))
        );

        assertEquals("API_DICOM_VALIDATION_FAILED", problem.code());
        assertEquals(422, problem.status());
        assertEquals(TRACE_ID, problem.traceId());
        assertFalse(problem.extensions().containsKey("filename"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> violations =
            (List<Map<String, Object>>) problem.extensions().get("violations");
        assertEquals(1, violations.size());
        assertEquals(0, violations.get(0).get("itemIndex"));
        assertEquals("MALFORMED_DICOM", violations.get(0).get("code"));
        assertEquals("The file is not valid DICOM.", violations.get(0).get("message"));
        assertFalse(violations.get(0).containsKey("filename"));
    }

    @Test
    void extension_that_does_not_belong_to_the_type_is_rejected() {
        ProblemExtensions violations = new ProblemExtensions.DicomValidationViolations(List.of(
            new ProblemExtensions.Violation(0, "MALFORMED_DICOM", "The file is not valid DICOM.")
        ));

        assertThrows(
            IllegalArgumentException.class,
            () -> factory.create(ProblemType.API_INTERNAL_ERROR, violations)
        );
    }

    @Test
    void client_scoped_type_is_not_an_http_response_and_is_rejected() {
        assertThrows(
            IllegalArgumentException.class,
            () -> factory.create(ProblemType.CLIENT_NETWORK_UNAVAILABLE, ProblemExtensions.none())
        );
    }

    @Test
    void serialization_carries_the_rfc_members_and_omits_instance_and_retry_policy() throws Exception {
        ApiProblem problem = factory.create(ProblemType.API_SEARCH_TOO_BROAD, ProblemExtensions.none());

        Map<String, Object> json = mapper.readValue(mapper.writeValueAsString(problem), Map.class);

        assertEquals(ProblemType.API_SEARCH_TOO_BROAD.type().toString(), json.get("type"));
        assertEquals("Search too broad", json.get("title"));
        assertEquals(413, json.get("status"));
        assertEquals("Refine the search filters and try again.", json.get("detail"));
        assertEquals("API_SEARCH_TOO_BROAD", json.get("code"));
        assertEquals(TRACE_ID, json.get("traceId"));
        assertFalse(json.containsKey("instance"));
        assertFalse(json.containsKey("retryPolicy"));
        assertFalse(json.containsKey("extensions"));
    }

    @Test
    void extension_members_are_serialized_at_the_root_level() throws Exception {
        ApiProblem problem = factory.create(
            ProblemType.API_DICOM_VALIDATION_FAILED,
            new ProblemExtensions.DicomValidationViolations(List.of(
                new ProblemExtensions.Violation(2, "SOP_UID_COLLISION", "Conflicting SOP Instance UID.")
            ))
        );

        Map<String, Object> json = mapper.readValue(mapper.writeValueAsString(problem), Map.class);

        assertTrue(json.containsKey("violations"));
        assertFalse(json.containsKey("extensions"));
    }

    @Test
    void a_problem_without_an_active_trace_omits_the_trace_id() {
        ApiProblemFactory withoutTrace = new ApiProblemFactory(new FixedTraceContext(null));

        assertNull(withoutTrace.create(ProblemType.API_INTERNAL_ERROR, ProblemExtensions.none()).traceId());
    }
}
