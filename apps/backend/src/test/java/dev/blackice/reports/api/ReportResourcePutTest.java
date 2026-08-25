package dev.blackice.reports.api;

import dev.blackice.reports.application.exception.ReportAccessDeniedException;
import dev.blackice.reports.application.exception.ReportConflictException;
import dev.blackice.reports.application.exception.ReportNotFoundException;
import dev.blackice.reports.application.exception.ReportVersionConflictException;
import dev.blackice.reports.application.input.UpdateReportCommand;
import dev.blackice.reports.application.result.StudyReportResult;
import dev.blackice.reports.application.usecase.CreateStudyReportUseCase;
import dev.blackice.reports.application.usecase.GetStudyReportUseCase;
import dev.blackice.reports.application.usecase.UpdateStudyReportUseCase;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.containsString;
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
class ReportResourcePutTest {

    private static final String FAILURE_LOGGER = "dev.blackice.shared.api.problem.ApiFailureLogger";
    private static final String VALID_STUDY_UID = "1.2.840.113619.2.55.3.604688435.123.1599720123.467";

    @InjectMock
    UpdateStudyReportUseCase updateStudyReportUseCase;

    @InjectMock
    CreateStudyReportUseCase createStudyReportUseCase;

    @InjectMock
    GetStudyReportUseCase getStudyReportUseCase;

    @InjectMock
    AccessTokenProvider accessTokenProvider;

    private String getCsrfToken() {
        return given()
            .when().get("/api/csrf")
            .then().statusCode(204)
            .extract().cookie("csrf-token");
    }

    @Test
    @DisplayName("anonymous PUT request receives 401 authentication required problem")
    void anonymous_request_receives_401() {
        given().redirects().follow(false)
            .header("If-Match", ReportEtag.fromVersion(0L).toString())
            .contentType(ContentType.JSON)
            .body("{\"content\":\"Updated content\",\"status\":\"DRAFT\"}")
            .when().put("/api/studies/" + VALID_STUDY_UID + "/report")
            .then().statusCode(401)
            .contentType("application/problem+json")
            .body("code", equalTo("API_AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(updateStudyReportUseCase);
        verifyNoInteractions(accessTokenProvider);
        verifyNoInteractions(createStudyReportUseCase);
        verifyNoInteractions(getStudyReportUseCase);
    }

    @Test
    @TestSecurity(user = "viewer-user", roles = "viewer")
    @DisplayName("user without auth role receives 403")
    void user_without_auth_role_receives_403() {
        String csrf = getCsrfToken();
        given().redirects().follow(false)
            .cookie("csrf-token", csrf)
            .header("X-CSRF-TOKEN", csrf)
            .header("If-Match", ReportEtag.fromVersion(0L).toString())
            .contentType(ContentType.JSON)
            .body("{\"content\":\"Updated content\",\"status\":\"DRAFT\"}")
            .when().put("/api/studies/" + VALID_STUDY_UID + "/report")
            .then().statusCode(403);

        verifyNoInteractions(updateStudyReportUseCase);
        verifyNoInteractions(accessTokenProvider);
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("request without CSRF header receives 403 API_CSRF_INVALID")
    void request_without_csrf_header_receives_403() {
        String csrf = getCsrfToken();
        given()
            .cookie("csrf-token", csrf)
            .header("If-Match", ReportEtag.fromVersion(0L).toString())
            .contentType(ContentType.JSON)
            .body("{\"content\":\"Updated content\",\"status\":\"DRAFT\"}")
            .when().put("/api/studies/" + VALID_STUDY_UID + "/report")
            .then().statusCode(403)
            .contentType("application/problem+json")
            .body("code", equalTo("API_CSRF_INVALID"));

        verifyNoInteractions(updateStudyReportUseCase);
        verifyNoInteractions(accessTokenProvider);
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("request with mismatched CSRF header receives 403 API_CSRF_INVALID")
    void request_with_mismatched_csrf_header_receives_403() {
        String csrf = getCsrfToken();
        given()
            .cookie("csrf-token", csrf)
            .header("X-CSRF-TOKEN", "mismatched-token-value")
            .header("If-Match", ReportEtag.fromVersion(0L).toString())
            .contentType(ContentType.JSON)
            .body("{\"content\":\"Updated content\",\"status\":\"DRAFT\"}")
            .when().put("/api/studies/" + VALID_STUDY_UID + "/report")
            .then().statusCode(403)
            .contentType("application/problem+json")
            .body("code", equalTo("API_CSRF_INVALID"));

        verifyNoInteractions(updateStudyReportUseCase);
        verifyNoInteractions(accessTokenProvider);
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("updates DRAFT report returning 200 with new strong ETag, no-store, and ReportResponse body")
    void updates_draft_report_successfully() {
        String csrf = getCsrfToken();
        String ifMatch = ReportEtag.fromVersion(0L).toString();

        Instant createdAt = Instant.parse("2026-08-25T14:00:00Z");
        Instant updatedAt = Instant.parse("2026-08-25T14:15:00Z");
        StudyReportResult updatedResult = new StudyReportResult(
            VALID_STUDY_UID,
            "dr.teste",
            ReportStatus.DRAFT,
            "Updated preliminary report content.",
            true,
            createdAt,
            updatedAt,
            null,
            1L
        );
        when(updateStudyReportUseCase.execute(any(UpdateReportCommand.class))).thenReturn(updatedResult);

        Response response = given()
            .cookie("csrf-token", csrf)
            .header("X-CSRF-TOKEN", csrf)
            .header("If-Match", ifMatch)
            .contentType(ContentType.JSON)
            .body("{\"content\":\"Updated preliminary report content.\",\"status\":\"DRAFT\"}")
            .when().put("/api/studies/" + VALID_STUDY_UID + "/report")
            .then().statusCode(200)
            .header("Cache-Control", equalTo("no-store"))
            .header("ETag", equalTo(ReportEtag.fromVersion(1L).toString()))
            .contentType(ContentType.JSON)
            .body("studyInstanceUid", equalTo(VALID_STUDY_UID))
            .body("authorDisplayName", equalTo("dr.teste"))
            .body("status", equalTo("DRAFT"))
            .body("content", equalTo("Updated preliminary report content."))
            .body("editable", equalTo(true))
            .body("createdAt", equalTo("2026-08-25T14:00:00Z"))
            .body("updatedAt", equalTo("2026-08-25T14:15:00Z"))
            .body("finalizedAt", nullValue())
            .extract().response();

        assertNotNull(response.header("X-Trace-ID"));
        assertNull(response.header("X-Request-ID"));

        ArgumentCaptor<UpdateReportCommand> captor = ArgumentCaptor.forClass(UpdateReportCommand.class);
        verify(updateStudyReportUseCase).execute(captor.capture());
        UpdateReportCommand captured = captor.getValue();
        assertEquals(VALID_STUDY_UID, captured.study().studyInstanceUid());
        assertEquals("dr.teste", captured.actor().subject());
        assertEquals("Updated preliminary report content.", captured.content().value());
        assertEquals(ReportStatus.DRAFT, captured.status());
        assertEquals(0L, captured.expectedVersion());

        verifyNoInteractions(accessTokenProvider);
        verifyNoInteractions(createStudyReportUseCase);
        verifyNoInteractions(getStudyReportUseCase);
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("updates DRAFT to FINAL report returning 200 with finalizedAt, editable false, and new ETag")
    void updates_draft_to_final_successfully() {
        String csrf = getCsrfToken();
        String ifMatch = ReportEtag.fromVersion(2L).toString();

        Instant createdAt = Instant.parse("2026-08-25T14:00:00Z");
        Instant finalizedAt = Instant.parse("2026-08-25T14:30:00Z");
        StudyReportResult updatedResult = new StudyReportResult(
            VALID_STUDY_UID,
            "dr.teste",
            ReportStatus.FINAL,
            "Final verified clinical diagnosis.",
            false,
            createdAt,
            finalizedAt,
            finalizedAt,
            3L
        );
        when(updateStudyReportUseCase.execute(any(UpdateReportCommand.class))).thenReturn(updatedResult);

        Response response = given()
            .cookie("csrf-token", csrf)
            .header("X-CSRF-TOKEN", csrf)
            .header("If-Match", ifMatch)
            .contentType(ContentType.JSON)
            .body("{\"content\":\"Final verified clinical diagnosis.\",\"status\":\"FINAL\"}")
            .when().put("/api/studies/" + VALID_STUDY_UID + "/report")
            .then().statusCode(200)
            .header("Cache-Control", equalTo("no-store"))
            .header("ETag", equalTo(ReportEtag.fromVersion(3L).toString()))
            .contentType(ContentType.JSON)
            .body("studyInstanceUid", equalTo(VALID_STUDY_UID))
            .body("authorDisplayName", equalTo("dr.teste"))
            .body("status", equalTo("FINAL"))
            .body("content", equalTo("Final verified clinical diagnosis."))
            .body("editable", equalTo(false))
            .body("createdAt", equalTo("2026-08-25T14:00:00Z"))
            .body("updatedAt", equalTo("2026-08-25T14:30:00Z"))
            .body("finalizedAt", equalTo("2026-08-25T14:30:00Z"))
            .extract().response();

        assertNotNull(response.header("X-Trace-ID"));
        assertNull(response.header("X-Request-ID"));

        ArgumentCaptor<UpdateReportCommand> captor = ArgumentCaptor.forClass(UpdateReportCommand.class);
        verify(updateStudyReportUseCase).execute(captor.capture());
        UpdateReportCommand captured = captor.getValue();
        assertEquals(VALID_STUDY_UID, captured.study().studyInstanceUid());
        assertEquals("dr.teste", captured.actor().subject());
        assertEquals("Final verified clinical diagnosis.", captured.content().value());
        assertEquals(ReportStatus.FINAL, captured.status());
        assertEquals(2L, captured.expectedVersion());

        verifyNoInteractions(accessTokenProvider);
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("missing If-Match header returns 400 API_REQUEST_INVALID")
    void missing_if_match_header_returns_400() {
        String csrf = getCsrfToken();
        given()
            .cookie("csrf-token", csrf)
            .header("X-CSRF-TOKEN", csrf)
            .contentType(ContentType.JSON)
            .body("{\"content\":\"Valid content\",\"status\":\"DRAFT\"}")
            .when().put("/api/studies/" + VALID_STUDY_UID + "/report")
            .then().statusCode(400)
            .contentType("application/problem+json")
            .body("code", equalTo("API_REQUEST_INVALID"))
            .body("traceId", not(blankOrNullString()));

        verifyNoInteractions(updateStudyReportUseCase);
        verifyNoInteractions(accessTokenProvider);
    }

    @ParameterizedTest(name = "malformed or invalid If-Match header \"{0}\" returns 400")
    @ValueSource(strings = {
        "",
        "   ",
        "*",
        "W/\"AAAAAAAAAAA\"",
        "w/\"AAAAAAAAAAA\"",
        "\"AAAAAAAAAAA\", \"BBBBBBBBBBB\"",
        "AAAAAAAAAAA",
        "\"short\"",
        "\"invalid!base64=\"",
        "\"AAAAAAAAAAAA\"",
        "\"\""
    })
    @TestSecurity(user = "dr.teste", roles = "auth")
    void invalid_if_match_headers_return_400(String ifMatchValue) {
        String csrf = getCsrfToken();
        given()
            .cookie("csrf-token", csrf)
            .header("X-CSRF-TOKEN", csrf)
            .header("If-Match", ifMatchValue)
            .contentType(ContentType.JSON)
            .body("{\"content\":\"Valid content\",\"status\":\"DRAFT\"}")
            .when().put("/api/studies/" + VALID_STUDY_UID + "/report")
            .then().statusCode(400)
            .contentType("application/problem+json")
            .body("code", equalTo("API_REQUEST_INVALID"))
            .body("traceId", not(blankOrNullString()));

        verifyNoInteractions(updateStudyReportUseCase);
        verifyNoInteractions(accessTokenProvider);
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("invalid study UID returns 400 API_REQUEST_INVALID")
    void invalid_study_uid_returns_400() {
        String csrf = getCsrfToken();
        Response response = given()
            .cookie("csrf-token", csrf)
            .header("X-CSRF-TOKEN", csrf)
            .header("If-Match", ReportEtag.fromVersion(0L).toString())
            .contentType(ContentType.JSON)
            .body("{\"content\":\"Some content\",\"status\":\"DRAFT\"}")
            .when().put("/api/studies/invalid-study-uid/report")
            .then().statusCode(400)
            .contentType("application/problem+json")
            .body("code", equalTo("API_REQUEST_INVALID"))
            .body("traceId", not(blankOrNullString()))
            .extract().response();

        assertNull(response.header("X-Request-ID"));
        assertEquals(response.jsonPath().getString("traceId"), response.header("X-Trace-ID"));
        verifyNoInteractions(updateStudyReportUseCase);
        verifyNoInteractions(accessTokenProvider);
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("empty or null body returns 400 API_REQUEST_INVALID")
    void empty_or_null_body_returns_400() {
        String csrf = getCsrfToken();
        given()
            .cookie("csrf-token", csrf)
            .header("X-CSRF-TOKEN", csrf)
            .header("If-Match", ReportEtag.fromVersion(0L).toString())
            .contentType(ContentType.JSON)
            .body("{}")
            .when().put("/api/studies/" + VALID_STUDY_UID + "/report")
            .then().statusCode(400)
            .contentType("application/problem+json")
            .body("code", equalTo("API_REQUEST_INVALID"));

        verifyNoInteractions(updateStudyReportUseCase);
        verifyNoInteractions(accessTokenProvider);
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("blank or whitespace-only content returns 400 API_REQUEST_INVALID")
    void blank_content_returns_400() {
        String csrf = getCsrfToken();
        given()
            .cookie("csrf-token", csrf)
            .header("X-CSRF-TOKEN", csrf)
            .header("If-Match", ReportEtag.fromVersion(0L).toString())
            .contentType(ContentType.JSON)
            .body("{\"content\":\"   \\t\\n  \",\"status\":\"DRAFT\"}")
            .when().put("/api/studies/" + VALID_STUDY_UID + "/report")
            .then().statusCode(400)
            .contentType("application/problem+json")
            .body("code", equalTo("API_REQUEST_INVALID"));

        verifyNoInteractions(updateStudyReportUseCase);
        verifyNoInteractions(accessTokenProvider);
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("invalid status returns 400 API_REQUEST_INVALID")
    void invalid_status_returns_400() {
        String csrf = getCsrfToken();
        given()
            .cookie("csrf-token", csrf)
            .header("X-CSRF-TOKEN", csrf)
            .header("If-Match", ReportEtag.fromVersion(0L).toString())
            .contentType(ContentType.JSON)
            .body("{\"content\":\"Valid text\",\"status\":\"UNKNOWN_STATUS\"}")
            .when().put("/api/studies/" + VALID_STUDY_UID + "/report")
            .then().statusCode(400)
            .contentType("application/problem+json")
            .body("code", equalTo("API_REQUEST_INVALID"));

        verifyNoInteractions(updateStudyReportUseCase);
        verifyNoInteractions(accessTokenProvider);
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("non-author user editing report returns 403 API_ACCESS_DENIED")
    void non_author_editing_report_returns_403() {
        String csrf = getCsrfToken();
        when(updateStudyReportUseCase.execute(any(UpdateReportCommand.class)))
            .thenThrow(new ReportAccessDeniedException("User is not the author of this report"));

        given()
            .cookie("csrf-token", csrf)
            .header("X-CSRF-TOKEN", csrf)
            .header("If-Match", ReportEtag.fromVersion(0L).toString())
            .contentType(ContentType.JSON)
            .body("{\"content\":\"Modified text\",\"status\":\"DRAFT\"}")
            .when().put("/api/studies/" + VALID_STUDY_UID + "/report")
            .then().statusCode(403)
            .contentType("application/problem+json")
            .body("code", equalTo("API_ACCESS_DENIED"))
            .body("traceId", not(blankOrNullString()));

        verifyNoInteractions(accessTokenProvider);
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("absent report returns 404 API_RESOURCE_NOT_FOUND")
    void absent_report_returns_404() {
        String csrf = getCsrfToken();
        when(updateStudyReportUseCase.execute(any(UpdateReportCommand.class)))
            .thenThrow(new ReportNotFoundException("Report not found for study"));

        given()
            .cookie("csrf-token", csrf)
            .header("X-CSRF-TOKEN", csrf)
            .header("If-Match", ReportEtag.fromVersion(0L).toString())
            .contentType(ContentType.JSON)
            .body("{\"content\":\"Modified text\",\"status\":\"DRAFT\"}")
            .when().put("/api/studies/" + VALID_STUDY_UID + "/report")
            .then().statusCode(404)
            .contentType("application/problem+json")
            .body("code", equalTo("API_RESOURCE_NOT_FOUND"))
            .body("traceId", not(blankOrNullString()));

        verifyNoInteractions(accessTokenProvider);
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("already FINAL report conflict returns 409 API_RESOURCE_CONFLICT")
    void already_final_report_conflict_returns_409() {
        String csrf = getCsrfToken();
        when(updateStudyReportUseCase.execute(any(UpdateReportCommand.class)))
            .thenThrow(new ReportConflictException("Report is in final state and cannot be modified"));

        given()
            .cookie("csrf-token", csrf)
            .header("X-CSRF-TOKEN", csrf)
            .header("If-Match", ReportEtag.fromVersion(0L).toString())
            .contentType(ContentType.JSON)
            .body("{\"content\":\"Modified text\",\"status\":\"DRAFT\"}")
            .when().put("/api/studies/" + VALID_STUDY_UID + "/report")
            .then().statusCode(409)
            .contentType("application/problem+json")
            .body("code", equalTo("API_RESOURCE_CONFLICT"))
            .body("traceId", not(blankOrNullString()));

        verifyNoInteractions(accessTokenProvider);
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("stale version precondition failure returns 412 API_RESOURCE_VERSION_CONFLICT")
    void stale_version_precondition_failure_returns_412() {
        String csrf = getCsrfToken();
        when(updateStudyReportUseCase.execute(any(UpdateReportCommand.class)))
            .thenThrow(new ReportVersionConflictException("Supplied version does not match current version"));

        given()
            .cookie("csrf-token", csrf)
            .header("X-CSRF-TOKEN", csrf)
            .header("If-Match", ReportEtag.fromVersion(0L).toString())
            .contentType(ContentType.JSON)
            .body("{\"content\":\"Modified text\",\"status\":\"DRAFT\"}")
            .when().put("/api/studies/" + VALID_STUDY_UID + "/report")
            .then().statusCode(412)
            .contentType("application/problem+json")
            .body("code", equalTo("API_RESOURCE_VERSION_CONFLICT"))
            .body("traceId", not(blankOrNullString()));

        verifyNoInteractions(accessTokenProvider);
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("oversized content with 32001 code points returns 413 API_PAYLOAD_TOO_LARGE")
    void oversized_content_returns_413() {
        String csrf = getCsrfToken();
        String oversizedContent = "A".repeat(32_001);

        given()
            .cookie("csrf-token", csrf)
            .header("X-CSRF-TOKEN", csrf)
            .header("If-Match", ReportEtag.fromVersion(0L).toString())
            .contentType(ContentType.JSON)
            .body("{\"content\":\"" + oversizedContent + "\",\"status\":\"DRAFT\"}")
            .when().put("/api/studies/" + VALID_STUDY_UID + "/report")
            .then().statusCode(413)
            .contentType("application/problem+json")
            .body("code", equalTo("API_PAYLOAD_TOO_LARGE"))
            .body("traceId", not(blankOrNullString()));

        verifyNoInteractions(updateStudyReportUseCase);
        verifyNoInteractions(accessTokenProvider);
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("unexpected runtime exception returns sanitized 500 API_INTERNAL_ERROR")
    void unexpected_exception_returns_sanitized_500() {
        String csrf = getCsrfToken();
        when(updateStudyReportUseCase.execute(any(UpdateReportCommand.class)))
            .thenThrow(new RuntimeException("secret-internal-database-error-details"));

        given()
            .cookie("csrf-token", csrf)
            .header("X-CSRF-TOKEN", csrf)
            .header("If-Match", ReportEtag.fromVersion(0L).toString())
            .contentType(ContentType.JSON)
            .body("{\"content\":\"Valid content\",\"status\":\"DRAFT\"}")
            .when().put("/api/studies/" + VALID_STUDY_UID + "/report")
            .then().statusCode(500)
            .contentType("application/problem+json")
            .body("code", equalTo("API_INTERNAL_ERROR"))
            .body(not(containsString("secret-internal-database-error-details")));

        verifyNoInteractions(accessTokenProvider);
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("failure logs record route template without leaking study UID or clinical content")
    void failure_logs_record_route_template_without_leaking_uid_or_content() {
        String traceId = "d92745a968984cbba81a95e7c81d34e3";
        String csrf = getCsrfToken();
        when(updateStudyReportUseCase.execute(any(UpdateReportCommand.class)))
            .thenThrow(new ReportVersionConflictException("Supplied version does not match current version"));

        try (ApiFailureLogCapture logs = ApiFailureLogCapture.start(FAILURE_LOGGER)) {
            given()
                .header("traceparent", "00-" + traceId + "-178596a0c80d4a92-01")
                .cookie("csrf-token", csrf)
                .header("X-CSRF-TOKEN", csrf)
                .header("If-Match", ReportEtag.fromVersion(0L).toString())
                .contentType(ContentType.JSON)
                .body("{\"content\":\"SECRET_CLINICAL_CONTENT_PUT_LEAK\",\"status\":\"DRAFT\"}")
                .when().put("/api/studies/" + VALID_STUDY_UID + "/report")
                .then().statusCode(412);

            List<LogRecord> events = logs.containing("traceId=" + traceId);
            assertEquals(1, events.size());
            assertEquals(Level.INFO, events.getFirst().getLevel());
            String event = logs.formatted(events.getFirst());
            assertTrue(event.contains("code=API_RESOURCE_VERSION_CONFLICT"));
            assertTrue(event.contains("status=412"));
            assertTrue(event.contains("method=PUT"));
            assertTrue(event.contains("route=/api/studies/{studyInstanceUid}/report"));
            assertTrue(event.contains("reason=VERSION_CONFLICT"));
            assertFalse(event.contains(VALID_STUDY_UID));
            assertFalse(event.contains("SECRET_CLINICAL_CONTENT_PUT_LEAK"));
            assertNull(events.getFirst().getThrown());
        }

        verifyNoInteractions(accessTokenProvider);
    }
}
