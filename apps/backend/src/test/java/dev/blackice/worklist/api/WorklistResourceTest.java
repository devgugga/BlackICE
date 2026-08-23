package dev.blackice.worklist.api;

import dev.blackice.security.application.AccessTokenProvider;
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

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class WorklistResourceTest {

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

        String requestId = response.header("X-Request-ID");
        assertNotNull(requestId);
        assertDoesNotThrow(() -> UUID.fromString(requestId));

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
            .body("code", equalTo("INVALID_SEARCH"))
            .body("message", equalTo("Review the supplied search filters."))
            .extract().response();

        String requestId = response.header("X-Request-ID");
        assertNotNull(requestId);
        assertDoesNotThrow(() -> UUID.fromString(requestId));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    void reversed_dates_returns_400_invalid_search() {
        given()
            .queryParam("dateFrom", "2026-08-22")
            .queryParam("dateTo", "2026-08-01")
            .when().get("/api/studies")
            .then().statusCode(400)
            .body("code", equalTo("INVALID_SEARCH"))
            .body("message", equalTo("Review the supplied search filters."));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    void wildcards_in_filters_returns_400_invalid_search() {
        given()
            .queryParam("patientName", "MARIA*")
            .when().get("/api/studies")
            .then().statusCode(400)
            .body("code", equalTo("INVALID_SEARCH"))
            .body("message", equalTo("Review the supplied search filters."));

        given()
            .queryParam("patientId", "123?")
            .when().get("/api/studies")
            .then().statusCode(400)
            .body("code", equalTo("INVALID_SEARCH"))
            .body("message", equalTo("Review the supplied search filters."));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    void invalid_modality_returns_400_invalid_search() {
        given()
            .queryParam("modality", "INVALID MODALITY!")
            .when().get("/api/studies")
            .then().statusCode(400)
            .body("code", equalTo("INVALID_SEARCH"))
            .body("message", equalTo("Review the supplied search filters."));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    void invalid_limit_or_offset_returns_400_invalid_search() {
        given()
            .queryParam("limit", 0)
            .when().get("/api/studies")
            .then().statusCode(400)
            .body("code", equalTo("INVALID_SEARCH"));

        given()
            .queryParam("limit", 101)
            .when().get("/api/studies")
            .then().statusCode(400)
            .body("code", equalTo("INVALID_SEARCH"));

        given()
            .queryParam("offset", -1)
            .when().get("/api/studies")
            .then().statusCode(400)
            .body("code", equalTo("INVALID_SEARCH"));
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
            .body("code", equalTo("SEARCH_TOO_BROAD"))
            .body("message", notNullValue())
            .extract().response();

        String requestId = response.header("X-Request-ID");
        assertNotNull(requestId);
        assertDoesNotThrow(() -> UUID.fromString(requestId));
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
            .body("code", equalTo("ARCHIVE_INVALID_RESPONSE"))
            .body("message", notNullValue())
            .extract().response();

        String requestId = response.header("X-Request-ID");
        assertNotNull(requestId);
        assertDoesNotThrow(() -> UUID.fromString(requestId));
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
            .body("code", equalTo("ARCHIVE_INVALID_RESPONSE"))
            .body("message", notNullValue())
            .extract().response();

        String requestId = response.header("X-Request-ID");
        assertNotNull(requestId);
        assertDoesNotThrow(() -> UUID.fromString(requestId));
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
            .body("code", equalTo("ARCHIVE_UNAVAILABLE"))
            .body("message", notNullValue())
            .extract().response();

        String requestId = response.header("X-Request-ID");
        assertNotNull(requestId);
        assertDoesNotThrow(() -> UUID.fromString(requestId));
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
            .body("code", equalTo("ARCHIVE_UNAVAILABLE"))
            .body("message", notNullValue())
            .extract().response();

        String requestId = response.header("X-Request-ID");
        assertNotNull(requestId);
        assertDoesNotThrow(() -> UUID.fromString(requestId));
    }
}
