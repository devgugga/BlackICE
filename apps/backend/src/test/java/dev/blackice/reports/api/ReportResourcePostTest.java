package dev.blackice.reports.api;

import dev.blackice.reports.application.exception.ArchiveStudyLookupException;
import dev.blackice.reports.application.exception.ReportConflictException;
import dev.blackice.reports.application.exception.StudyNotFoundException;
import dev.blackice.reports.application.input.CreateReportCommand;
import dev.blackice.reports.application.result.StudyReportResult;
import dev.blackice.reports.application.usecase.CreateStudyReportUseCase;
import dev.blackice.reports.domain.ReportStatus;
import dev.blackice.security.application.AccessTokenProvider;
import dev.blackice.shared.api.problem.ApiFailureLogCapture;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@QuarkusTest
class ReportResourcePostTest {

    private static final String FAILURE_LOGGER = "dev.blackice.shared.api.problem.ApiFailureLogger";
    private static final String VALID_STUDY_UID = "1.2.840.113619.2.55.3.604688435.123.1599720123.467";

    @InjectMock
    CreateStudyReportUseCase createStudyReportUseCase;

    @InjectMock
    AccessTokenProvider accessTokenProvider;

    private String getCsrfToken() {
        return given()
            .when().get("/api/csrf")
            .then().statusCode(204)
            .extract().cookie("csrf-token");
    }

    @Test
    @DisplayName("anonymous request receives 401 authentication required problem")
    void anonymous_request_receives_401() {
        given().redirects().follow(false)
            .contentType(ContentType.JSON)
            .body("{\"content\":\"Draft content\",\"status\":\"DRAFT\"}")
            .when().post("/api/studies/" + VALID_STUDY_UID + "/report")
            .then().statusCode(401)
            .contentType("application/problem+json")
            .body("code", equalTo("API_AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(createStudyReportUseCase);
    }

    @Test
    @TestSecurity(user = "viewer-user", roles = "viewer")
    @DisplayName("user without auth role receives 403")
    void user_without_auth_role_receives_403() {
        String csrf = getCsrfToken();
        given().redirects().follow(false)
            .cookie("csrf-token", csrf)
            .header("X-CSRF-TOKEN", csrf)
            .contentType(ContentType.JSON)
            .body("{\"content\":\"Draft content\",\"status\":\"DRAFT\"}")
            .when().post("/api/studies/" + VALID_STUDY_UID + "/report")
            .then().statusCode(403);

        verifyNoInteractions(createStudyReportUseCase);
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("request without CSRF header receives 403 API_CSRF_INVALID")
    void request_without_csrf_header_receives_403() {
        String csrf = getCsrfToken();
        given()
            .cookie("csrf-token", csrf)
            .contentType(ContentType.JSON)
            .body("{\"content\":\"Draft content\",\"status\":\"DRAFT\"}")
            .when().post("/api/studies/" + VALID_STUDY_UID + "/report")
            .then().statusCode(403)
            .contentType("application/problem+json")
            .body("code", equalTo("API_CSRF_INVALID"));

        verifyNoInteractions(createStudyReportUseCase);
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("request with mismatched CSRF header receives 403 API_CSRF_INVALID")
    void request_with_mismatched_csrf_header_receives_403() {
        String csrf = getCsrfToken();
        given()
            .cookie("csrf-token", csrf)
            .header("X-CSRF-TOKEN", "mismatched-token-value")
            .contentType(ContentType.JSON)
            .body("{\"content\":\"Draft content\",\"status\":\"DRAFT\"}")
            .when().post("/api/studies/" + VALID_STUDY_UID + "/report")
            .then().statusCode(403)
            .contentType("application/problem+json")
            .body("code", equalTo("API_CSRF_INVALID"));

        verifyNoInteractions(createStudyReportUseCase);
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("creates DRAFT report returning 201 with Location, strong ETag, no-store, and ReportResponse body")
    void creates_draft_report_successfully() {
        String csrf = getCsrfToken();
        when(accessTokenProvider.accessToken()).thenReturn("bearer-token-123");

        Instant createdTime = Instant.parse("2026-08-25T14:00:00Z");
        StudyReportResult createdResult = new StudyReportResult(
            VALID_STUDY_UID,
            "dr.teste",
            ReportStatus.DRAFT,
            "Normal chest examination findings.",
            true,
            createdTime,
            createdTime,
            null,
            0L
        );
        when(createStudyReportUseCase.execute(any(CreateReportCommand.class))).thenReturn(createdResult);

        Response response = given()
            .cookie("csrf-token", csrf)
            .header("X-CSRF-TOKEN", csrf)
            .contentType(ContentType.JSON)
            .body("{\"content\":\"Normal chest examination findings.\",\"status\":\"DRAFT\"}")
            .when().post("/api/studies/" + VALID_STUDY_UID + "/report")
            .then().statusCode(201)
            .header("Location", endsWith("/api/studies/" + VALID_STUDY_UID + "/report"))
            .header("Cache-Control", equalTo("no-store"))
            .header("ETag", equalTo(ReportEtag.fromVersion(0L).toString()))
            .contentType(ContentType.JSON)
            .body("studyInstanceUid", equalTo(VALID_STUDY_UID))
            .body("authorDisplayName", equalTo("dr.teste"))
            .body("status", equalTo("DRAFT"))
            .body("content", equalTo("Normal chest examination findings."))
            .body("editable", equalTo(true))
            .body("createdAt", equalTo("2026-08-25T14:00:00Z"))
            .body("updatedAt", equalTo("2026-08-25T14:00:00Z"))
            .body("finalizedAt", nullValue())
            .extract().response();

        assertNotNull(response.header("X-Trace-ID"));
        assertNull(response.header("X-Request-ID"));

        ArgumentCaptor<CreateReportCommand> captor = ArgumentCaptor.forClass(CreateReportCommand.class);
        verify(createStudyReportUseCase).execute(captor.capture());
        CreateReportCommand captured = captor.getValue();
        assertEquals(VALID_STUDY_UID, captured.study().studyInstanceUid());
        assertEquals("dr.teste", captured.actor().subject());
        assertEquals("Normal chest examination findings.", captured.content().value());
        assertEquals(ReportStatus.DRAFT, captured.status());
        assertEquals("bearer-token-123", captured.accessToken());
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("creates direct FINAL report returning 201 with finalizedAt and editable false")
    void creates_direct_final_report_successfully() {
        String csrf = getCsrfToken();
        when(accessTokenProvider.accessToken()).thenReturn("bearer-token-123");

        Instant createdTime = Instant.parse("2026-08-25T14:30:00Z");
        StudyReportResult createdResult = new StudyReportResult(
            VALID_STUDY_UID,
            "dr.teste",
            ReportStatus.FINAL,
            "Immediate final diagnosis text.",
            false,
            createdTime,
            createdTime,
            createdTime,
            0L
        );
        when(createStudyReportUseCase.execute(any(CreateReportCommand.class))).thenReturn(createdResult);

        Response response = given()
            .cookie("csrf-token", csrf)
            .header("X-CSRF-TOKEN", csrf)
            .contentType(ContentType.JSON)
            .body("{\"content\":\"Immediate final diagnosis text.\",\"status\":\"FINAL\"}")
            .when().post("/api/studies/" + VALID_STUDY_UID + "/report")
            .then().statusCode(201)
            .header("Location", endsWith("/api/studies/" + VALID_STUDY_UID + "/report"))
            .header("Cache-Control", equalTo("no-store"))
            .header("ETag", equalTo(ReportEtag.fromVersion(0L).toString()))
            .contentType(ContentType.JSON)
            .body("studyInstanceUid", equalTo(VALID_STUDY_UID))
            .body("authorDisplayName", equalTo("dr.teste"))
            .body("status", equalTo("FINAL"))
            .body("content", equalTo("Immediate final diagnosis text."))
            .body("editable", equalTo(false))
            .body("createdAt", equalTo("2026-08-25T14:30:00Z"))
            .body("updatedAt", equalTo("2026-08-25T14:30:00Z"))
            .body("finalizedAt", equalTo("2026-08-25T14:30:00Z"))
            .extract().response();

        assertNotNull(response.header("X-Trace-ID"));
        assertNull(response.header("X-Request-ID"));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("invalid study UID returns 400 API_REQUEST_INVALID")
    void invalid_study_uid_returns_400() {
        String csrf = getCsrfToken();
        Response response = given()
            .cookie("csrf-token", csrf)
            .header("X-CSRF-TOKEN", csrf)
            .contentType(ContentType.JSON)
            .body("{\"content\":\"Some content\",\"status\":\"DRAFT\"}")
            .when().post("/api/studies/invalid-study-uid/report")
            .then().statusCode(400)
            .contentType("application/problem+json")
            .body("code", equalTo("API_REQUEST_INVALID"))
            .body("traceId", not(blankOrNullString()))
            .extract().response();

        assertNull(response.header("X-Request-ID"));
        assertEquals(response.jsonPath().getString("traceId"), response.header("X-Trace-ID"));
        verifyNoInteractions(createStudyReportUseCase);
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("empty or null body returns 400 API_REQUEST_INVALID")
    void empty_or_null_body_returns_400() {
        String csrf = getCsrfToken();
        given()
            .cookie("csrf-token", csrf)
            .header("X-CSRF-TOKEN", csrf)
            .contentType(ContentType.JSON)
            .body("{}")
            .when().post("/api/studies/" + VALID_STUDY_UID + "/report")
            .then().statusCode(400)
            .contentType("application/problem+json")
            .body("code", equalTo("API_REQUEST_INVALID"));

        verifyNoInteractions(createStudyReportUseCase);
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("blank or whitespace-only content returns 400 API_REQUEST_INVALID")
    void blank_content_returns_400() {
        String csrf = getCsrfToken();
        given()
            .cookie("csrf-token", csrf)
            .header("X-CSRF-TOKEN", csrf)
            .contentType(ContentType.JSON)
            .body("{\"content\":\"   \\t\\n  \",\"status\":\"DRAFT\"}")
            .when().post("/api/studies/" + VALID_STUDY_UID + "/report")
            .then().statusCode(400)
            .contentType("application/problem+json")
            .body("code", equalTo("API_REQUEST_INVALID"));

        verifyNoInteractions(createStudyReportUseCase);
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("invalid status returns 400 API_REQUEST_INVALID")
    void invalid_status_returns_400() {
        String csrf = getCsrfToken();
        given()
            .cookie("csrf-token", csrf)
            .header("X-CSRF-TOKEN", csrf)
            .contentType(ContentType.JSON)
            .body("{\"content\":\"Valid text\",\"status\":\"INVALID_STATUS\"}")
            .when().post("/api/studies/" + VALID_STUDY_UID + "/report")
            .then().statusCode(400)
            .contentType("application/problem+json")
            .body("code", equalTo("API_REQUEST_INVALID"));

        verifyNoInteractions(createStudyReportUseCase);
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("content with 32001 code points returns 413 API_PAYLOAD_TOO_LARGE")
    void oversized_content_returns_413() {
        String csrf = getCsrfToken();
        String oversizedContent = "A".repeat(32_001);

        given()
            .cookie("csrf-token", csrf)
            .header("X-CSRF-TOKEN", csrf)
            .contentType(ContentType.JSON)
            .body("{\"content\":\"" + oversizedContent + "\",\"status\":\"DRAFT\"}")
            .when().post("/api/studies/" + VALID_STUDY_UID + "/report")
            .then().statusCode(413)
            .contentType("application/problem+json")
            .body("code", equalTo("API_PAYLOAD_TOO_LARGE"))
            .body("traceId", not(blankOrNullString()));

        verifyNoInteractions(createStudyReportUseCase);
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("nonexistent study in archive returns 404 API_RESOURCE_NOT_FOUND")
    void nonexistent_study_returns_404() {
        String csrf = getCsrfToken();
        when(accessTokenProvider.accessToken()).thenReturn("bearer-token-123");
        when(createStudyReportUseCase.execute(any(CreateReportCommand.class)))
            .thenThrow(new StudyNotFoundException("Study does not exist in archive"));

        given()
            .cookie("csrf-token", csrf)
            .header("X-CSRF-TOKEN", csrf)
            .contentType(ContentType.JSON)
            .body("{\"content\":\"Valid content\",\"status\":\"DRAFT\"}")
            .when().post("/api/studies/" + VALID_STUDY_UID + "/report")
            .then().statusCode(404)
            .contentType("application/problem+json")
            .body("code", equalTo("API_RESOURCE_NOT_FOUND"))
            .body("traceId", not(blankOrNullString()));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("report conflict or insert race returns 409 API_RESOURCE_CONFLICT")
    void report_conflict_returns_409() {
        String csrf = getCsrfToken();
        when(accessTokenProvider.accessToken()).thenReturn("bearer-token-123");
        when(createStudyReportUseCase.execute(any(CreateReportCommand.class)))
            .thenThrow(new ReportConflictException("Report already exists for study"));

        given()
            .cookie("csrf-token", csrf)
            .header("X-CSRF-TOKEN", csrf)
            .contentType(ContentType.JSON)
            .body("{\"content\":\"Valid content\",\"status\":\"DRAFT\"}")
            .when().post("/api/studies/" + VALID_STUDY_UID + "/report")
            .then().statusCode(409)
            .contentType("application/problem+json")
            .body("code", equalTo("API_RESOURCE_CONFLICT"))
            .body("traceId", not(blankOrNullString()));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("archive invalid response returns 502 API_ARCHIVE_RESPONSE_INVALID")
    void archive_invalid_response_returns_502() {
        String csrf = getCsrfToken();
        when(accessTokenProvider.accessToken()).thenReturn("bearer-token-123");
        when(createStudyReportUseCase.execute(any(CreateReportCommand.class)))
            .thenThrow(new ArchiveStudyLookupException(ArchiveStudyLookupException.Reason.ARCHIVE_INVALID_RESPONSE));

        given()
            .cookie("csrf-token", csrf)
            .header("X-CSRF-TOKEN", csrf)
            .contentType(ContentType.JSON)
            .body("{\"content\":\"Valid content\",\"status\":\"DRAFT\"}")
            .when().post("/api/studies/" + VALID_STUDY_UID + "/report")
            .then().statusCode(502)
            .contentType("application/problem+json")
            .body("code", equalTo("API_ARCHIVE_RESPONSE_INVALID"))
            .body("traceId", not(blankOrNullString()));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("archive unavailable or timeout returns 503 API_ARCHIVE_UNAVAILABLE")
    void archive_unavailable_returns_503() {
        String csrf = getCsrfToken();
        when(accessTokenProvider.accessToken()).thenReturn("bearer-token-123");
        when(createStudyReportUseCase.execute(any(CreateReportCommand.class)))
            .thenThrow(new ArchiveStudyLookupException(ArchiveStudyLookupException.Reason.ARCHIVE_UNAVAILABLE));

        given()
            .cookie("csrf-token", csrf)
            .header("X-CSRF-TOKEN", csrf)
            .contentType(ContentType.JSON)
            .body("{\"content\":\"Valid content\",\"status\":\"DRAFT\"}")
            .when().post("/api/studies/" + VALID_STUDY_UID + "/report")
            .then().statusCode(503)
            .contentType("application/problem+json")
            .body("code", equalTo("API_ARCHIVE_UNAVAILABLE"))
            .body("traceId", not(blankOrNullString()));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("archive auth failed returns 502 API_ARCHIVE_RESPONSE_INVALID")
    void archive_auth_failed_returns_502() {
        String csrf = getCsrfToken();
        when(accessTokenProvider.accessToken()).thenReturn("bearer-token-123");
        when(createStudyReportUseCase.execute(any(CreateReportCommand.class)))
            .thenThrow(new ArchiveStudyLookupException(ArchiveStudyLookupException.Reason.ARCHIVE_AUTH_FAILED));

        given()
            .cookie("csrf-token", csrf)
            .header("X-CSRF-TOKEN", csrf)
            .contentType(ContentType.JSON)
            .body("{\"content\":\"Valid content\",\"status\":\"DRAFT\"}")
            .when().post("/api/studies/" + VALID_STUDY_UID + "/report")
            .then().statusCode(502)
            .contentType("application/problem+json")
            .body("code", equalTo("API_ARCHIVE_RESPONSE_INVALID"))
            .body("traceId", not(blankOrNullString()));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("unexpected runtime exception returns sanitized 500 API_INTERNAL_ERROR")
    void unexpected_exception_returns_sanitized_500() {
        String csrf = getCsrfToken();
        when(accessTokenProvider.accessToken()).thenReturn("bearer-token-123");
        when(createStudyReportUseCase.execute(any(CreateReportCommand.class)))
            .thenThrow(new RuntimeException("secret-internal-database-error-details"));

        given()
            .cookie("csrf-token", csrf)
            .header("X-CSRF-TOKEN", csrf)
            .contentType(ContentType.JSON)
            .body("{\"content\":\"Valid content\",\"status\":\"DRAFT\"}")
            .when().post("/api/studies/" + VALID_STUDY_UID + "/report")
            .then().statusCode(500)
            .contentType("application/problem+json")
            .body("code", equalTo("API_INTERNAL_ERROR"))
            .body(not(containsString("secret-internal-database-error-details")));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("failure logs record route template without leaking study UID or clinical content")
    void failure_logs_record_route_template_without_leaking_uid_or_content() {
        String traceId = "c82635a968984cbba81a95e7c81d34e2";
        String csrf = getCsrfToken();
        when(accessTokenProvider.accessToken()).thenReturn("bearer-token-123");
        when(createStudyReportUseCase.execute(any(CreateReportCommand.class)))
            .thenThrow(new ReportConflictException("Report already exists for study"));

        try (ApiFailureLogCapture logs = ApiFailureLogCapture.start(FAILURE_LOGGER)) {
            given()
                .header("traceparent", "00-" + traceId + "-178596a0c80d4a91-01")
                .cookie("csrf-token", csrf)
                .header("X-CSRF-TOKEN", csrf)
                .contentType(ContentType.JSON)
                .body("{\"content\":\"SECRET_CLINICAL_CONTENT_LEAK\",\"status\":\"DRAFT\"}")
                .when().post("/api/studies/" + VALID_STUDY_UID + "/report")
                .then().statusCode(409);

            List<LogRecord> events = logs.containing("traceId=" + traceId);
            assertEquals(1, events.size());
            assertEquals(Level.INFO, events.getFirst().getLevel());
            String event = logs.formatted(events.getFirst());
            assertTrue(event.contains("code=API_RESOURCE_CONFLICT"));
            assertTrue(event.contains("status=409"));
            assertTrue(event.contains("method=POST"));
            assertTrue(event.contains("route=/api/studies/{studyInstanceUid}/report"));
            assertTrue(event.contains("reason=CONFLICT"));
            assertFalse(event.contains(VALID_STUDY_UID));
            assertFalse(event.contains("SECRET_CLINICAL_CONTENT_LEAK"));
            assertNull(events.getFirst().getThrown());
        }
    }
}
