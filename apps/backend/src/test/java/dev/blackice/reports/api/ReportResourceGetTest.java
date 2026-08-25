package dev.blackice.reports.api;

import dev.blackice.reports.application.input.ReportStudyRef;
import dev.blackice.reports.application.result.StudyReportResult;
import dev.blackice.reports.application.usecase.GetStudyReportUseCase;
import dev.blackice.reports.domain.ReportStatus;
import dev.blackice.security.application.AccessTokenProvider;
import dev.blackice.shared.api.problem.ApiFailureLogCapture;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@QuarkusTest
class ReportResourceGetTest {

    private static final String FAILURE_LOGGER = "dev.blackice.shared.api.problem.ApiFailureLogger";
    private static final String VALID_STUDY_UID = "1.2.840.113619.2.55.3.604688435.123.1599720123.467";

    @InjectMock
    GetStudyReportUseCase getStudyReportUseCase;

    @InjectMock
    AccessTokenProvider accessTokenProvider;

    @Test
    @DisplayName("anonymous request receives 401 problem")
    void anonymous_request_receives_401() {
        given().redirects().follow(false)
            .when().get("/api/studies/" + VALID_STUDY_UID + "/report")
            .then().statusCode(401)
            .contentType("application/problem+json")
            .body("code", equalTo("API_AUTHENTICATION_REQUIRED"));
    }

    @Test
    @TestSecurity(user = "viewer-user", roles = "viewer")
    @DisplayName("user without auth role receives 403")
    void user_without_auth_role_receives_403() {
        given().when().get("/api/studies/" + VALID_STUDY_UID + "/report")
            .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("invalid study UID returns 400 invalid request problem")
    void invalid_study_uid_returns_400_invalid_request() {
        Response response = given()
            .when().get("/api/studies/not-a-valid-uid/report")
            .then().statusCode(400)
            .contentType("application/problem+json")
            .body("code", equalTo("API_REQUEST_INVALID"))
            .body("detail", equalTo("The request is invalid or malformed."))
            .body("traceId", not(blankOrNullString()))
            .extract().response();

        assertNull(response.header("X-Request-ID"));
        assertEquals(response.jsonPath().getString("traceId"), response.header("X-Trace-ID"));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("absent report returns 204 with no body, no ETag, and no-store")
    void absent_report_returns_204_with_no_body_no_etag_and_no_store() {
        when(getStudyReportUseCase.execute(eq(new ReportStudyRef(VALID_STUDY_UID)), any()))
            .thenReturn(Optional.empty());

        Response response = given()
            .when().get("/api/studies/" + VALID_STUDY_UID + "/report")
            .then().statusCode(204)
            .header("Cache-Control", equalTo("no-store"))
            .header("ETag", nullValue())
            .extract().response();

        assertTrue(response.body().asString().isEmpty());
        assertNotNull(response.header("X-Trace-ID"));
        assertNull(response.header("X-Request-ID"));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("existing report returns 200 with JSON, ETag, and no-store")
    void existing_report_returns_200_with_json_etag_and_no_store() {
        StudyReportResult sample = new StudyReportResult(
            VALID_STUDY_UID,
            "Dr. Teste",
            ReportStatus.DRAFT,
            "Normal study report content.",
            true,
            Instant.parse("2026-08-25T12:00:00Z"),
            Instant.parse("2026-08-25T12:30:00Z"),
            null,
            1L
        );
        when(getStudyReportUseCase.execute(eq(new ReportStudyRef(VALID_STUDY_UID)), any()))
            .thenReturn(Optional.of(sample));

        Response response = given()
            .when().get("/api/studies/" + VALID_STUDY_UID + "/report")
            .then().statusCode(200)
            .header("Cache-Control", equalTo("no-store"))
            .header("ETag", equalTo(ReportEtag.fromVersion(1L).toString()))
            .contentType("application/json")
            .body("studyInstanceUid", equalTo(VALID_STUDY_UID))
            .body("authorDisplayName", equalTo("Dr. Teste"))
            .body("status", equalTo("DRAFT"))
            .body("content", equalTo("Normal study report content."))
            .body("editable", equalTo(true))
            .body("createdAt", equalTo("2026-08-25T12:00:00Z"))
            .body("updatedAt", equalTo("2026-08-25T12:30:00Z"))
            .body("finalizedAt", nullValue())
            .extract().response();

        assertNotNull(response.header("X-Trace-ID"));
        assertNull(response.header("X-Request-ID"));
    }

    @Test
    @TestSecurity(user = "dr.leitor", roles = "auth")
    @DisplayName("other user report returns editable false")
    void other_user_report_returns_editable_false() {
        StudyReportResult sample = new StudyReportResult(
            VALID_STUDY_UID,
            "Dr. Teste",
            ReportStatus.DRAFT,
            "Draft by Dr. Teste.",
            false,
            Instant.parse("2026-08-25T12:00:00Z"),
            Instant.parse("2026-08-25T12:30:00Z"),
            null,
            1L
        );
        when(getStudyReportUseCase.execute(eq(new ReportStudyRef(VALID_STUDY_UID)), any()))
            .thenReturn(Optional.of(sample));

        given().when().get("/api/studies/" + VALID_STUDY_UID + "/report")
            .then().statusCode(200)
            .header("Cache-Control", equalTo("no-store"))
            .body("editable", equalTo(false));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("GET succeeds without CSRF cookie or header")
    void get_succeeds_without_csrf_tokens() {
        when(getStudyReportUseCase.execute(eq(new ReportStudyRef(VALID_STUDY_UID)), any()))
            .thenReturn(Optional.empty());

        given()
            .when().get("/api/studies/" + VALID_STUDY_UID + "/report")
            .then().statusCode(204);
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("GET does not invoke AccessTokenProvider or StudyExistenceGateway")
    void get_does_not_invoke_access_token_provider_or_qido_gateway() {
        when(getStudyReportUseCase.execute(eq(new ReportStudyRef(VALID_STUDY_UID)), any()))
            .thenReturn(Optional.empty());

        given()
            .when().get("/api/studies/" + VALID_STUDY_UID + "/report")
            .then().statusCode(204);

        verifyNoInteractions(accessTokenProvider);
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("invalid study UID emits safe info log event with route template without leaking UID")
    void invalid_study_uid_emits_safe_info_event() {
        String traceId = "96f725d0d0514392b29417b5ae8d1a1b";

        try (ApiFailureLogCapture logs = ApiFailureLogCapture.start(FAILURE_LOGGER)) {
            given()
                .header("traceparent", "00-" + traceId + "-178596a0c80d4a91-01")
                .when().get("/api/studies/INVALID-STUDY-UID-LEAK/report")
                .then().statusCode(400);

            List<LogRecord> events = logs.containing("traceId=" + traceId);
            assertEquals(1, events.size());
            assertEquals(Level.INFO, events.getFirst().getLevel());
            String event = logs.formatted(events.getFirst());
            assertTrue(event.contains("code=API_REQUEST_INVALID"));
            assertTrue(event.contains("status=400"));
            assertTrue(event.contains("method=GET"));
            assertTrue(event.contains("route=/api/studies/{studyInstanceUid}/report"));
            assertTrue(event.contains("reason=INVALID_REQUEST"));
            assertFalse(event.contains("INVALID-STUDY-UID-LEAK"));
            assertNull(events.getFirst().getThrown());
        }
    }
}
