package dev.blackice.worklist.api;

import dev.blackice.security.application.AccessTokenProvider;
import dev.blackice.shared.api.problem.ApiFailureLogCapture;
import dev.blackice.worklist.application.exception.ArchiveSearchException;
import dev.blackice.worklist.application.result.StudyPage;
import dev.blackice.worklist.application.result.StudySummary;
import dev.blackice.worklist.application.usecase.SearchStudiesUseCase;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class WorklistResourceTest {

    private static final String FAILURE_LOGGER = "dev.blackice.shared.api.problem.ApiFailureLogger";

    @InjectMock
    SearchStudiesUseCase useCase;

    @InjectMock
    AccessTokenProvider accessToken;

    private StudyPage page() {
        StudySummary summary = new StudySummary(
            "1.2.3",
            "MARIA^SILVA",
            "123",
            "HOSPITAL-A",
            "2026-08-22",
            "10:35:12",
            List.of("CT"),
            "CT CHEST",
            3,
            187
        );
        return new StudyPage(List.of(summary), new StudyPage.PageMetadata(20, 0, false, false));
    }

    @Test
    void anonymous_get_receives_a_catalogued_authentication_problem() {
        given().redirects().follow(false).when().get("/api/studies")
            .then().statusCode(401)
            .contentType("application/problem+json")
            .body("code", equalTo("API_AUTHENTICATION_REQUIRED"));
    }

    @Test
    @TestSecurity(user = "user", roles = "viewer")
    void user_without_auth_role_receives_403() {
        given().when().get("/api/studies").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    void valid_query_returns_curated_page_and_calls_use_case_once() {
        when(accessToken.accessToken()).thenReturn("user-token");
        when(useCase.search(any(), eq("user-token"))).thenReturn(page());

        Response response = given().queryParam("patientName", "MARIA")
            .queryParam("limit", 20).queryParam("offset", 0)
            .when().get("/api/studies")
            .then().statusCode(200)
            .body("items[0].studyInstanceUid", equalTo("1.2.3"))
            .body("items[0].patientName", equalTo("MARIA^SILVA"))
            .body("items[0].patientId", equalTo("123"))
            .body("items[0].patientIdIssuer", equalTo("HOSPITAL-A"))
            .body("items[0].studyDate", equalTo("2026-08-22"))
            .body("items[0].studyTime", equalTo("10:35:12"))
            .body("items[0].modalities[0]", equalTo("CT"))
            .body("items[0].description", equalTo("CT CHEST"))
            .body("items[0].seriesCount", equalTo(3))
            .body("items[0].instanceCount", equalTo(187))
            .body("page.limit", equalTo(20))
            .body("page.offset", equalTo(0))
            .body("page.hasPrevious", equalTo(false))
            .body("page.hasNext", equalTo(false))
            .extract().response();

        assertNull(response.header("X-Request-ID"));
        assertNotNull(response.header("X-Trace-ID"));

        verify(useCase, times(1)).search(any(), eq("user-token"));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    void default_limit_and_offset_when_omitted_and_no_csrf_needed() {
        when(accessToken.accessToken()).thenReturn("user-token");
        when(useCase.search(any(), eq("user-token"))).thenReturn(page());

        given()
            .when().get("/api/studies")
            .then().statusCode(200)
            .body("items[0].studyInstanceUid", equalTo("1.2.3"))
            .body("page.limit", equalTo(20))
            .body("page.offset", equalTo(0));

        verify(useCase, times(1)).search(
            argThat(request -> request.limit() == 20
                && request.offset() == 0
                && request.patientName() == null
                && request.patientId() == null
                && request.modality() == null
                && request.dateFrom() == null
                && request.dateTo() == null),
            eq("user-token")
        );
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    void invalid_iso_date_returns_400_invalid_search() {
        Response response = given()
            .queryParam("dateFrom", "not-a-date")
            .when().get("/api/studies")
            .then().statusCode(400)
            .contentType("application/problem+json")
            .body("code", equalTo("API_SEARCH_INVALID"))
            .body("detail", equalTo("Review the supplied search filters."))
            .body("traceId", not(blankOrNullString()))
            .extract().response();

        assertNull(response.header("X-Request-ID"));
        assertEquals(response.jsonPath().getString("traceId"), response.header("X-Trace-ID"));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    void reversed_dates_returns_400_invalid_search() {
        given()
            .queryParam("dateFrom", "2026-08-22")
            .queryParam("dateTo", "2026-08-01")
            .when().get("/api/studies")
            .then().statusCode(400)
            .contentType("application/problem+json")
            .body("code", equalTo("API_SEARCH_INVALID"))
            .body("detail", equalTo("Review the supplied search filters."));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    void wildcards_in_filters_returns_400_invalid_search() {
        given()
            .queryParam("patientName", "MARIA*")
            .when().get("/api/studies")
            .then().statusCode(400)
            .contentType("application/problem+json")
            .body("code", equalTo("API_SEARCH_INVALID"))
            .body("detail", equalTo("Review the supplied search filters."));

        given()
            .queryParam("patientId", "123?")
            .when().get("/api/studies")
            .then().statusCode(400)
            .contentType("application/problem+json")
            .body("code", equalTo("API_SEARCH_INVALID"))
            .body("detail", equalTo("Review the supplied search filters."));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    void invalid_modality_returns_400_invalid_search() {
        given()
            .queryParam("modality", "INVALID MODALITY!")
            .when().get("/api/studies")
            .then().statusCode(400)
            .contentType("application/problem+json")
            .body("code", equalTo("API_SEARCH_INVALID"))
            .body("detail", equalTo("Review the supplied search filters."));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    void invalid_limit_or_offset_returns_400_invalid_search() {
        given()
            .queryParam("limit", 0)
            .when().get("/api/studies")
            .then().statusCode(400)
            .contentType("application/problem+json")
            .body("code", equalTo("API_SEARCH_INVALID"));

        given()
            .queryParam("limit", 101)
            .when().get("/api/studies")
            .then().statusCode(400)
            .contentType("application/problem+json")
            .body("code", equalTo("API_SEARCH_INVALID"));

        given()
            .queryParam("offset", -1)
            .when().get("/api/studies")
            .then().statusCode(400)
            .contentType("application/problem+json")
            .body("code", equalTo("API_SEARCH_INVALID"));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    void an_unexpected_failure_is_not_disguised_as_archive_unavailability() {
        when(accessToken.accessToken()).thenReturn("user-token");
        when(useCase.search(any(), eq("user-token")))
            .thenThrow(new RuntimeException("patient-secret"));

        Response response = given()
            .when().get("/api/studies")
            .then().statusCode(500)
            .contentType("application/problem+json")
            .body("code", equalTo("API_INTERNAL_ERROR"))
            .body("detail", not(containsString("patient-secret")))
            .body("traceId", not(blankOrNullString()))
            .extract().response();

        assertNull(response.header("X-Request-ID"));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    void archive_query_too_broad_returns_413_search_too_broad() {
        when(accessToken.accessToken()).thenReturn("user-token");
        when(useCase.search(any(), eq("user-token")))
            .thenThrow(new ArchiveSearchException(ArchiveSearchException.Reason.QUERY_TOO_BROAD, null));

        Response response = given()
            .when().get("/api/studies")
            .then().statusCode(413)
            .contentType("application/problem+json")
            .body("code", equalTo("API_SEARCH_TOO_BROAD"))
            .body("traceId", not(blankOrNullString()))
            .extract().response();

        assertNull(response.header("X-Request-ID"));
        assertEquals(response.jsonPath().getString("traceId"), response.header("X-Trace-ID"));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    void archive_invalid_response_returns_502_archive_invalid_response() {
        when(accessToken.accessToken()).thenReturn("user-token");
        when(useCase.search(any(), eq("user-token")))
            .thenThrow(new ArchiveSearchException(ArchiveSearchException.Reason.INVALID_RESPONSE, null));

        Response response = given()
            .when().get("/api/studies")
            .then().statusCode(502)
            .contentType("application/problem+json")
            .body("code", equalTo("API_ARCHIVE_RESPONSE_INVALID"))
            .body("traceId", not(blankOrNullString()))
            .extract().response();

        assertNull(response.header("X-Request-ID"));
        assertEquals(response.jsonPath().getString("traceId"), response.header("X-Trace-ID"));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    void archive_http_status_returns_502_archive_invalid_response() {
        when(accessToken.accessToken()).thenReturn("user-token");
        when(useCase.search(any(), eq("user-token")))
            .thenThrow(new ArchiveSearchException(ArchiveSearchException.Reason.HTTP_STATUS, null));

        Response response = given()
            .when().get("/api/studies")
            .then().statusCode(502)
            .contentType("application/problem+json")
            .body("code", equalTo("API_ARCHIVE_RESPONSE_INVALID"))
            .body("traceId", not(blankOrNullString()))
            .extract().response();

        assertNull(response.header("X-Request-ID"));
        assertEquals(response.jsonPath().getString("traceId"), response.header("X-Trace-ID"));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    void archive_timeout_returns_503_archive_unavailable() {
        when(accessToken.accessToken()).thenReturn("user-token");
        when(useCase.search(any(), eq("user-token")))
            .thenThrow(new ArchiveSearchException(ArchiveSearchException.Reason.TIMEOUT, null));

        Response response = given()
            .when().get("/api/studies")
            .then().statusCode(503)
            .contentType("application/problem+json")
            .body("code", equalTo("API_ARCHIVE_UNAVAILABLE"))
            .body("traceId", not(blankOrNullString()))
            .extract().response();

        assertNull(response.header("X-Request-ID"));
        assertEquals(response.jsonPath().getString("traceId"), response.header("X-Trace-ID"));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    void known_archive_failure_emits_exactly_one_safe_warn_event() {
        String traceId = "8192f2b6129343ca9be36fd74be7a708";
        when(accessToken.accessToken()).thenReturn("user-token");
        when(useCase.search(any(), eq("user-token")))
            .thenThrow(new ArchiveSearchException(
                ArchiveSearchException.Reason.TIMEOUT,
                new IllegalStateException("external-cause patient-secret 1.2.840.113619")));

        try (ApiFailureLogCapture logs = ApiFailureLogCapture.start(FAILURE_LOGGER)) {
            given()
                .header("traceparent", "00-" + traceId + "-7b9a559cd4474348-01")
                .queryParam("patientName", "MARIA-PATIENT-SECRET")
                .when().get("/api/studies")
                .then().statusCode(503);

            List<LogRecord> events = logs.containing("traceId=" + traceId);
            assertEquals(1, events.size());
            assertEquals(Level.WARNING, events.getFirst().getLevel());
            String event = logs.formatted(events.getFirst());
            assertTrue(event.contains("code=API_ARCHIVE_UNAVAILABLE"));
            assertTrue(event.contains("status=503"));
            assertTrue(event.contains("method=GET"));
            assertTrue(event.contains("route=/api/studies"));
            assertTrue(event.contains("reason=TIMEOUT"));
            assertFalse(event.contains("MARIA-PATIENT-SECRET"));
            assertFalse(event.contains("1.2.840"));
            assertFalse(event.contains("external-cause"));
            assertNull(events.getFirst().getThrown());
        }
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    void invalid_search_emits_exactly_one_safe_info_event() {
        String traceId = "96f725d0d0514392b29417b5ae8d1a1b";

        try (ApiFailureLogCapture logs = ApiFailureLogCapture.start(FAILURE_LOGGER)) {
            given()
                .header("traceparent", "00-" + traceId + "-178596a0c80d4a91-01")
                .queryParam("dateFrom", "PATIENT-SECRET-NOT-A-DATE")
                .when().get("/api/studies")
                .then().statusCode(400);

            List<LogRecord> events = logs.containing("traceId=" + traceId);
            assertEquals(1, events.size());
            assertEquals(Level.INFO, events.getFirst().getLevel());
            String event = logs.formatted(events.getFirst());
            assertTrue(event.contains("code=API_SEARCH_INVALID"));
            assertTrue(event.contains("status=400"));
            assertTrue(event.contains("reason=INVALID_SEARCH"));
            assertFalse(event.contains("PATIENT-SECRET-NOT-A-DATE"));
            assertNull(events.getFirst().getThrown());
        }
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    void archive_connection_failure_returns_503_archive_unavailable() {
        when(accessToken.accessToken()).thenReturn("user-token");
        when(useCase.search(any(), eq("user-token")))
            .thenThrow(new ArchiveSearchException(ArchiveSearchException.Reason.CONNECTION, null));

        Response response = given()
            .when().get("/api/studies")
            .then().statusCode(503)
            .contentType("application/problem+json")
            .body("code", equalTo("API_ARCHIVE_UNAVAILABLE"))
            .body("traceId", not(blankOrNullString()))
            .extract().response();

        assertNull(response.header("X-Request-ID"));
        assertEquals(response.jsonPath().getString("traceId"), response.header("X-Trace-ID"));
    }
}
