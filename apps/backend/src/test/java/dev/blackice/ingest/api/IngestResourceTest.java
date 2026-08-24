package dev.blackice.ingest.api;

import dev.blackice.ingest.application.input.UploadedDicom;
import dev.blackice.shared.api.problem.ApiProblemFactory;
import dev.blackice.shared.api.problem.ApiProblem;
import dev.blackice.shared.api.problem.ApiFailureLogCapture;
import dev.blackice.shared.api.problem.ProblemResponseFactory;
import dev.blackice.shared.api.problem.TraceContext;
import dev.blackice.ingest.application.result.IngestResult;
import dev.blackice.ingest.application.validation.DicomValidationIssue;
import dev.blackice.ingest.application.result.StowInstanceResult;
import dev.blackice.ingest.application.usecase.IngestStudiesUseCase;
import dev.blackice.security.application.AccessTokenProvider;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.response.Response;
import jakarta.ws.rs.core.Response.Status;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasToString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class IngestResourceTest {

    private static final String FAILURE_LOGGER = "dev.blackice.shared.api.problem.ApiFailureLogger";

    @InjectMock
    IngestStudiesUseCase useCase;

    @InjectMock
    AccessTokenProvider accessToken;

    private String getCsrfToken() {
        return given()
            .when().get("/api/csrf")
            .then().statusCode(204)
            .extract().cookie("csrf-token");
    }

    @Test
    void anonymous_request_receives_a_catalogued_authentication_problem() {
        given().redirects().follow(false)
            .multiPart("files", "test.dcm", new byte[] {1, 2, 3}, "application/dicom")
            .when().post("/api/studies")
            .then().statusCode(401)
            .contentType("application/problem+json")
            .body("code", equalTo("API_AUTHENTICATION_REQUIRED"));
    }

    @Test
    @TestSecurity(user = "user", roles = "viewer")
    void user_without_auth_role_receives_403() {
        String csrf = getCsrfToken();
        given().redirects().follow(false)
            .cookie("csrf-token", csrf)
            .header("X-CSRF-TOKEN", csrf)
            .multiPart("files", "test.dcm", new byte[] {1, 2, 3}, "application/dicom")
            .when().post("/api/studies")
            .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    void request_without_csrf_header_receives_a_catalogued_verification_problem() {
        String csrf = getCsrfToken();
        given()
            .cookie("csrf-token", csrf)
            .multiPart("files", "test.dcm", new byte[] {1, 2, 3}, "application/dicom")
            .when().post("/api/studies")
            .then().statusCode(403)
            .contentType("application/problem+json")
            .body("code", equalTo("API_CSRF_INVALID"));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    void request_with_csrf_cookie_and_header_calls_use_case_and_returns_success() {
        String csrf = getCsrfToken();
        when(accessToken.accessToken()).thenReturn("test-token");

        IngestResult.Summary summary = new IngestResult.Summary(1, 1, 0, 1, 0);
        IngestResult.StudyResult studyResult = new IngestResult.StudyResult(
            "1.2.3",
            IngestResult.StudyStatus.COMPLETE,
            List.of(new IngestResult.InstanceResult("1.2.3.1", StowInstanceResult.Status.ACCEPTED, null)),
            null
        );
        IngestResult responseBody = new IngestResult(
            IngestResult.Outcome.COMPLETE,
            summary,
            List.of(studyResult),
            List.of()
        );
        when(useCase.ingest(anyList(), eq("test-token"))).thenReturn(responseBody);

        Response response = given()
            .cookie("csrf-token", csrf)
            .header("X-CSRF-TOKEN", csrf)
            .multiPart("files", "test.dcm", new byte[] {1, 2, 3}, "application/dicom")
            .when().post("/api/studies")
            .then()
            .statusCode(200)
            .body("outcome", equalTo("COMPLETE"))
            .body("summary.received", equalTo(1))
            .body("summary.archiveAccepted", equalTo(1))
            .body("studies[0].studyInstanceUid", equalTo("1.2.3"))
            .extract().response();

        assertNull(response.header("X-Request-ID"));
        assertNotNull(response.header("X-Trace-ID"));
        verify(useCase).ingest(anyList(), eq("test-token"));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    void batch_with_no_locally_valid_file_returns_catalogued_violations_without_filenames() {
        String traceId = "e1d61bc93a294567a081617319c6b52f";
        String csrf = getCsrfToken();
        when(accessToken.accessToken()).thenReturn("test-token");
        when(useCase.ingest(anyList(), eq("test-token"))).thenReturn(new IngestResult(
            IngestResult.Outcome.FAILED,
            new IngestResult.Summary(1, 0, 1, 0, 0),
            List.of(),
            List.of(new IngestResult.RejectedFile(
                0, "PATIENT-SECRET.dcm", DicomValidationIssue.Code.MALFORMED_DICOM, "The file is not valid DICOM."))
        ));

        try (ApiFailureLogCapture logs = ApiFailureLogCapture.start(FAILURE_LOGGER)) {
            given()
                .header("traceparent", "00-" + traceId + "-6b414d61e91845da-01")
                .cookie("csrf-token", csrf)
                .header("X-CSRF-TOKEN", csrf)
                .multiPart("files", "PATIENT-SECRET.dcm", new byte[] {1, 2, 3}, "application/dicom")
                .when().post("/api/studies")
                .then()
                .statusCode(422)
                .contentType("application/problem+json")
                .body("code", equalTo("API_DICOM_VALIDATION_FAILED"))
                .body("violations[0].itemIndex", equalTo(0))
                .body("violations[0].code", equalTo("MALFORMED_DICOM"))
                .body("violations[0].message", equalTo("The file is not valid DICOM."))
                .body("violations[0].filename", nullValue())
                .body("traceId", equalTo(traceId));

            assertSingleSafeFailure(logs, traceId, Level.INFO,
                "API_DICOM_VALIDATION_FAILED", 422, "LOCAL_VALIDATION");
        }
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    void batch_whose_valid_studies_all_fail_by_unavailability_returns_a_bare_503() {
        String traceId = "7533f4087e9c42ce8fb9b03e8fd51366";
        String csrf = getCsrfToken();
        when(accessToken.accessToken()).thenReturn("test-token");
        when(useCase.ingest(anyList(), eq("test-token"))).thenReturn(new IngestResult(
            IngestResult.Outcome.FAILED,
            new IngestResult.Summary(1, 1, 0, 0, 0),
            List.of(new IngestResult.StudyResult(
                "1.2.840.113619.2.55.3", IngestResult.StudyStatus.FAILED, List.of(), "CONNECTION")),
            List.of()
        ));

        try (ApiFailureLogCapture logs = ApiFailureLogCapture.start(FAILURE_LOGGER)) {
            given()
                .header("traceparent", "00-" + traceId + "-a34cb288658543cb-01")
                .cookie("csrf-token", csrf)
                .header("X-CSRF-TOKEN", csrf)
                .multiPart("files", "PATIENT-SECRET.dcm", new byte[] {1, 2, 3}, "application/dicom")
                .when().post("/api/studies")
                .then()
                .statusCode(503)
                .contentType("application/problem+json")
                .body("code", equalTo("API_ARCHIVE_UNAVAILABLE"))
                .body("studies", nullValue())
                .body("summary", nullValue())
                .body("violations", nullValue())
                .body("$", not(hasToString(containsString("1.2.840"))))
                .body("traceId", equalTo(traceId));

            assertSingleSafeFailure(logs, traceId, Level.WARNING,
                "API_ARCHIVE_UNAVAILABLE", 503, "CONNECTION");
        }
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    void an_archive_that_answered_with_an_unusable_body_is_not_reported_as_unavailable() {
        String traceId = "f890ed765b174da19f3675d6617a1d94";
        String csrf = getCsrfToken();
        when(accessToken.accessToken()).thenReturn("test-token");
        when(useCase.ingest(anyList(), eq("test-token"))).thenReturn(new IngestResult(
            IngestResult.Outcome.FAILED,
            new IngestResult.Summary(1, 1, 0, 0, 0),
            List.of(new IngestResult.StudyResult(
                "1.2.840.113619.2.55.3", IngestResult.StudyStatus.FAILED, List.of(), "INVALID_RESPONSE")),
            List.of()
        ));

        try (ApiFailureLogCapture logs = ApiFailureLogCapture.start(FAILURE_LOGGER)) {
            given()
                .header("traceparent", "00-" + traceId + "-865a38da1c214b2f-01")
                .cookie("csrf-token", csrf)
                .header("X-CSRF-TOKEN", csrf)
                .multiPart("files", "PATIENT-SECRET.dcm", new byte[] {1, 2, 3}, "application/dicom")
                .when().post("/api/studies")
                .then()
                .statusCode(502)
                .contentType("application/problem+json")
                .body("code", equalTo("API_ARCHIVE_RESPONSE_INVALID"))
                .body("traceId", equalTo(traceId));

            assertSingleSafeFailure(logs, traceId, Level.WARNING,
                "API_ARCHIVE_RESPONSE_INVALID", 502, "INVALID_RESPONSE");
        }
    }

    @Test
    void confirmed_success_and_unknown_outcome_remain_a_partial_200_result() {
        IngestResult result = new IngestResult(
            IngestResult.Outcome.PARTIAL,
            new IngestResult.Summary(2, 2, 0, 1, 1),
            List.of(
                new IngestResult.StudyResult("1.2.3", IngestResult.StudyStatus.COMPLETE,
                    List.of(new IngestResult.InstanceResult(
                        "1.2.3.1", StowInstanceResult.Status.ACCEPTED, null)), null),
                new IngestResult.StudyResult("1.2.4", IngestResult.StudyStatus.FAILED,
                    List.of(), "OUTCOME_UNKNOWN")
            ),
            List.of()
        );

        jakarta.ws.rs.core.Response response = mapper().toResponse(result);

        assertEquals(200, response.getStatus());
        assertEquals(result, response.getEntity());
    }

    @Test
    void confirmed_rejection_and_unknown_outcome_remain_a_failed_200_result() {
        IngestResult result = new IngestResult(
            IngestResult.Outcome.FAILED,
            new IngestResult.Summary(2, 2, 0, 0, 2),
            List.of(
                new IngestResult.StudyResult("1.2.3", IngestResult.StudyStatus.FAILED,
                    List.of(new IngestResult.InstanceResult(
                        "1.2.3.1", StowInstanceResult.Status.REJECTED, 272)), null),
                new IngestResult.StudyResult("1.2.4", IngestResult.StudyStatus.FAILED,
                    List.of(), "OUTCOME_UNKNOWN")
            ),
            List.of()
        );

        jakarta.ws.rs.core.Response response = mapper().toResponse(result);

        assertEquals(200, response.getStatus());
        assertEquals(result, response.getEntity());
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    void all_unknown_outcomes_return_sanitized_catalogued_502() {
        String csrf = getCsrfToken();
        when(accessToken.accessToken()).thenReturn("test-token");
        when(useCase.ingest(anyList(), eq("test-token"))).thenReturn(new IngestResult(
            IngestResult.Outcome.FAILED,
            new IngestResult.Summary(1, 1, 0, 0, 1),
            List.of(new IngestResult.StudyResult(
                "1.2.840.113619.2.55.3", IngestResult.StudyStatus.FAILED, List.of(), "OUTCOME_UNKNOWN")),
            List.of(new IngestResult.RejectedFile(
                0, "patient-secret.dcm", DicomValidationIssue.Code.MALFORMED_DICOM, "safe"))
        ));

        given()
            .cookie("csrf-token", csrf)
            .header("X-CSRF-TOKEN", csrf)
            .multiPart("files", "test.dcm", new byte[] {1, 2, 3}, "application/dicom")
            .when().post("/api/studies")
            .then()
            .statusCode(502)
            .contentType("application/problem+json")
            .body("code", equalTo("API_ARCHIVE_OUTCOME_UNKNOWN"))
            .body("studies", nullValue())
            .body("summary", nullValue())
            .body("violations", nullValue())
            .body("$", not(hasToString(containsString("1.2.840"))))
            .body("$", not(hasToString(containsString("patient-secret"))));
    }

    @Test
    void unknown_outcome_dominates_unavailability_when_nothing_is_confirmable() {
        IngestResult result = failedAttempts("OUTCOME_UNKNOWN", "CONNECTION");

        jakarta.ws.rs.core.Response response = mapper().toResponse(result);

        assertEquals(502, response.getStatus());
        assertEquals("API_ARCHIVE_OUTCOME_UNKNOWN", ((ApiProblem) response.getEntity()).code());
    }

    @Test
    void only_http_status_and_invalid_response_use_existing_invalid_archive_problem() {
        IngestResult result = failedAttempts("HTTP_STATUS", "INVALID_RESPONSE");

        jakarta.ws.rs.core.Response response = mapper().toResponse(result);

        assertEquals(502, response.getStatus());
        assertEquals("API_ARCHIVE_RESPONSE_INVALID", ((ApiProblem) response.getEntity()).code());
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    void upload_without_files_returns_400() {
        String csrf = getCsrfToken();
        given()
            .cookie("csrf-token", csrf)
            .header("X-CSRF-TOKEN", csrf)
            .multiPart("dummy", "empty")
            .when().post("/api/studies")
            .then().statusCode(400)
            .contentType("application/problem+json")
            .body("code", equalTo("API_UPLOAD_EMPTY"))
            .body("traceId", not(blankOrNullString()));
    }

    /** Real problem boundary with a fixed TraceID for unit tests. */
    private static ProblemResponseFactory problemResponses() {
        return new ProblemResponseFactory(new ApiProblemFactory(new TraceContext() {
            @Override
            public String traceId() {
                return "4bf92f3577b34da6a3ce929d0e0e4736";
            }

            @Override
            public String spanId() {
                return "00f067aa0ba902b7";
            }
        }));
    }

    private static IngestResponseMapper mapper() {
        TraceContext traceContext = new TraceContext() {
            @Override
            public String traceId() {
                return "4bf92f3577b34da6a3ce929d0e0e4736";
            }
        };
        return new IngestResponseMapper(problemResponses(), new dev.blackice.shared.api.problem.ApiFailureLogger(traceContext));
    }

    private static IngestResult failedAttempts(String... reasons) {
        List<IngestResult.StudyResult> studies = new ArrayList<>(reasons.length);
        for (int index = 0; index < reasons.length; index++) {
            studies.add(new IngestResult.StudyResult(
                "1.2." + (index + 3), IngestResult.StudyStatus.FAILED, List.of(), reasons[index]));
        }
        return new IngestResult(
            IngestResult.Outcome.FAILED,
            new IngestResult.Summary(reasons.length, reasons.length, 0, 0, reasons.length),
            List.copyOf(studies),
            List.of()
        );
    }

    private static void assertSingleSafeFailure(
        ApiFailureLogCapture logs,
        String traceId,
        Level level,
        String code,
        int status,
        String reason
    ) {
        List<LogRecord> events = logs.containing("traceId=" + traceId);
        assertEquals(1, events.size());
        assertEquals(level, events.getFirst().getLevel());
        String event = logs.formatted(events.getFirst());
        assertTrue(event.contains("code=" + code));
        assertTrue(event.contains("status=" + status));
        assertTrue(event.contains("method=POST"));
        assertTrue(event.contains("route=/api/studies"));
        assertTrue(event.contains("reason=" + reason));
        assertFalse(event.contains("PATIENT-SECRET"));
        assertFalse(event.contains("1.2.840"));
        assertNull(events.getFirst().getThrown());
    }

    @Test
    void upload_of_501_mocked_files_returns_413_without_calling_use_case() {
        IngestStudiesUseCase mockUseCase = mock(IngestStudiesUseCase.class);
        AccessTokenProvider mockToken = mock(AccessTokenProvider.class);
        IngestResource resource = new IngestResource(
            mockUseCase, mockToken, mapper(), problemResponses(), 500, 524288000L);

        List<FileUpload> files = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            FileUpload file = mock(FileUpload.class);
            when(file.size()).thenReturn(10L);
            when(file.fileName()).thenReturn("file-" + i + ".dcm");
            when(file.uploadedFile()).thenReturn(Path.of("/tmp/file-" + i + ".dcm"));
            files.add(file);
        }

        jakarta.ws.rs.core.Response response = resource.ingest(files);
        assertEquals(Status.REQUEST_ENTITY_TOO_LARGE.getStatusCode(), response.getStatus());
        verify(mockUseCase, never()).ingest(anyList(), anyString());
    }

    @Test
    void upload_larger_than_500mb_returns_413_without_calling_use_case() {
        IngestStudiesUseCase mockUseCase = mock(IngestStudiesUseCase.class);
        AccessTokenProvider mockToken = mock(AccessTokenProvider.class);
        IngestResource resource = new IngestResource(
            mockUseCase, mockToken, mapper(), problemResponses(), 500, 524288000L);

        FileUpload file = mock(FileUpload.class);
        when(file.size()).thenReturn(524288001L);
        when(file.fileName()).thenReturn("huge.dcm");
        when(file.uploadedFile()).thenReturn(Path.of("/tmp/huge.dcm"));

        jakarta.ws.rs.core.Response response = resource.ingest(List.of(file));
        assertEquals(Status.REQUEST_ENTITY_TOO_LARGE.getStatusCode(), response.getStatus());
        verify(mockUseCase, never()).ingest(anyList(), anyString());
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    void framework_managed_temporary_file_is_removed_after_request() {
        String csrf = getCsrfToken();
        when(accessToken.accessToken()).thenReturn("test-token");

        AtomicReference<Path> uploadedTempPath = new AtomicReference<>();
        IngestResult resultResponse = new IngestResult(
            IngestResult.Outcome.COMPLETE,
            new IngestResult.Summary(1, 1, 0, 1, 0),
            List.of(new IngestResult.StudyResult(
                "1.2.3",
                IngestResult.StudyStatus.COMPLETE,
                List.of(new IngestResult.InstanceResult(
                    "1.2.3.1", StowInstanceResult.Status.ACCEPTED, null)),
                null
            )),
            List.of()
        );

        when(useCase.ingest(anyList(), anyString())).thenAnswer(invocation -> {
            List<UploadedDicom> uploads = invocation.getArgument(0);
            Path path = uploads.get(0).path();
            uploadedTempPath.set(path);
            assertTrue(Files.exists(path), "The temporary file must exist while the use case is running");
            return resultResponse;
        });

        given()
            .cookie("csrf-token", csrf)
            .header("X-CSRF-TOKEN", csrf)
            .multiPart("files", "temp-test.dcm", new byte[] {1, 2, 3, 4}, "application/dicom")
            .when().post("/api/studies")
            .then().statusCode(200);

        assertNotNull(uploadedTempPath.get(), "The temporary path must be captured");
        assertFalse(Files.exists(uploadedTempPath.get()), "Quarkus must remove the temporary file after the request");
    }
}
