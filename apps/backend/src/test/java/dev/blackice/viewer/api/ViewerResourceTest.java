package dev.blackice.viewer.api;

import dev.blackice.security.application.AccessTokenProvider;
import dev.blackice.shared.api.problem.ApiFailureLogCapture;
import dev.blackice.viewer.application.exception.ArchiveViewerException;
import dev.blackice.viewer.application.exception.InvalidArchiveMetadataException;
import dev.blackice.viewer.application.input.ViewerSeriesRef;
import dev.blackice.viewer.application.input.ViewerStudyRef;
import dev.blackice.viewer.application.result.SeriesAvailability;
import dev.blackice.viewer.application.result.StudyViewerSummary;
import dev.blackice.viewer.application.result.UnsupportedReason;
import dev.blackice.viewer.application.result.ViewerInstance;
import dev.blackice.viewer.application.result.ViewerSeriesInstances;
import dev.blackice.viewer.application.result.ViewerSeriesSummary;
import dev.blackice.viewer.application.usecase.GetSeriesInstancesUseCase;
import dev.blackice.viewer.application.usecase.GetStudyViewerUseCase;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class ViewerResourceTest {

    private static final String FAILURE_LOGGER = "dev.blackice.shared.api.problem.ApiFailureLogger";

    private static final String VALID_STUDY_UID = "1.2.840.113619.2.55.3.604688435.123.1599720123.467";
    private static final String VALID_SERIES_UID = "1.2.840.113619.2.55.3.604688435.123.1599720123.468";
    private static final String VALID_SOP_INSTANCE_UID = "1.2.840.113619.2.55.3.604688435.123.1599720123.469";
    private static final String CT_SOP_CLASS = "1.2.840.10008.5.1.4.1.1.2";

    @InjectMock
    GetStudyViewerUseCase getStudyViewerUseCase;

    @InjectMock
    GetSeriesInstancesUseCase getSeriesInstancesUseCase;

    @InjectMock
    AccessTokenProvider accessTokenProvider;

    private StudyViewerSummary sampleStudySummary() {
        return new StudyViewerSummary(
            VALID_STUDY_UID,
            "MARIA^SILVA",
            "12345",
            "HOSPITAL-A",
            "20260824",
            "143000",
            "CT CHEST",
            List.of(
                new ViewerSeriesSummary(
                    VALID_SERIES_UID,
                    1,
                    "CT",
                    "AXIAL",
                    50,
                    SeriesAvailability.SUPPORTED,
                    null
                ),
                new ViewerSeriesSummary(
                    "1.2.840.113619.2.55.3.604688435.123.1599720123.999",
                    2,
                    "SR",
                    "REPORT",
                    1,
                    SeriesAvailability.UNSUPPORTED,
                    UnsupportedReason.NON_IMAGE_OBJECT
                )
            )
        );
    }

    private ViewerSeriesInstances sampleSeriesInstances() {
        ViewerInstance instance = new ViewerInstance(
            VALID_SOP_INSTANCE_UID,
            CT_SOP_CLASS,
            1,
            512,
            512,
            1,
            "MONOCHROME2",
            16,
            12,
            11,
            0,
            null,
            new double[]{-150.0, -150.0, 10.0},
            new double[]{1.0, 0.0, 0.0, 0.0, 1.0, 0.0},
            new double[]{0.5, 0.5},
            "1.2.840.113619.2.55.3.604688435.123.1599720123.1000",
            -1024.0,
            1.0,
            List.of(40.0),
            List.of(400.0)
        );
        return new ViewerSeriesInstances(
            VALID_STUDY_UID,
            VALID_SERIES_UID,
            List.of(instance)
        );
    }

    // =========================================================================
    // GET /api/studies/{studyUid}
    // =========================================================================

    @Test
    @DisplayName("anonymous study request receives 401 problem")
    void anonymous_study_request_receives_401() {
        given().redirects().follow(false)
            .when().get("/api/studies/" + VALID_STUDY_UID)
            .then().statusCode(401)
            .contentType("application/problem+json")
            .body("code", equalTo("API_AUTHENTICATION_REQUIRED"));
    }

    @Test
    @TestSecurity(user = "viewer-user", roles = "viewer")
    @DisplayName("user without auth role receives 403 on study request")
    void user_without_auth_role_receives_403() {
        given().when().get("/api/studies/" + VALID_STUDY_UID)
            .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("valid study request returns curated summary and calls use case once")
    void valid_study_request_returns_curated_summary() {
        when(accessTokenProvider.accessToken()).thenReturn("user-token");
        when(getStudyViewerUseCase.execute(eq(new ViewerStudyRef(VALID_STUDY_UID)), eq("user-token")))
            .thenReturn(sampleStudySummary());

        Response response = given()
            .when().get("/api/studies/" + VALID_STUDY_UID)
            .then().statusCode(200)
            .body("studyInstanceUid", equalTo(VALID_STUDY_UID))
            .body("patientName", equalTo("MARIA^SILVA"))
            .body("patientId", equalTo("12345"))
            .body("patientIdIssuer", equalTo("HOSPITAL-A"))
            .body("studyDate", equalTo("20260824"))
            .body("studyTime", equalTo("143000"))
            .body("description", equalTo("CT CHEST"))
            .body("series[0].seriesInstanceUid", equalTo(VALID_SERIES_UID))
            .body("series[0].seriesNumber", equalTo(1))
            .body("series[0].modality", equalTo("CT"))
            .body("series[0].description", equalTo("AXIAL"))
            .body("series[0].instanceCount", equalTo(50))
            .body("series[0].availability", equalTo("SUPPORTED"))
            .body("series[1].availability", equalTo("UNSUPPORTED"))
            .body("series[1].unsupportedReason", equalTo("NON_IMAGE_OBJECT"))
            .extract().response();

        assertNull(response.header("X-Request-ID"));
        assertNotNull(response.header("X-Trace-ID"));

        verify(getStudyViewerUseCase, times(1))
            .execute(eq(new ViewerStudyRef(VALID_STUDY_UID)), eq("user-token"));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("invalid study UID returns 400 invalid request problem")
    void invalid_study_uid_returns_400_invalid_request() {
        Response response = given()
            .when().get("/api/studies/not-a-valid-uid")
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
    @DisplayName("archive authentication failure returns 401 problem")
    void study_archive_authentication_failure_returns_401() {
        when(accessTokenProvider.accessToken()).thenReturn("user-token");
        when(getStudyViewerUseCase.execute(any(), eq("user-token")))
            .thenThrow(new ArchiveViewerException(ArchiveViewerException.Reason.AUTHENTICATION));

        given().when().get("/api/studies/" + VALID_STUDY_UID)
            .then().statusCode(401)
            .contentType("application/problem+json")
            .body("code", equalTo("API_AUTHENTICATION_REQUIRED"));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("archive access denied returns 403 problem")
    void study_archive_access_denied_returns_403() {
        when(accessTokenProvider.accessToken()).thenReturn("user-token");
        when(getStudyViewerUseCase.execute(any(), eq("user-token")))
            .thenThrow(new ArchiveViewerException(ArchiveViewerException.Reason.ACCESS_DENIED));

        given().when().get("/api/studies/" + VALID_STUDY_UID)
            .then().statusCode(403)
            .contentType("application/problem+json")
            .body("code", equalTo("API_ACCESS_DENIED"));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("archive not found returns 404 problem")
    void study_archive_not_found_returns_404() {
        when(accessTokenProvider.accessToken()).thenReturn("user-token");
        when(getStudyViewerUseCase.execute(any(), eq("user-token")))
            .thenThrow(new ArchiveViewerException(ArchiveViewerException.Reason.NOT_FOUND));

        given().when().get("/api/studies/" + VALID_STUDY_UID)
            .then().statusCode(404)
            .contentType("application/problem+json")
            .body("code", equalTo("API_RESOURCE_NOT_FOUND"));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("archive unavailable returns 503 problem")
    void study_archive_unavailable_returns_503() {
        when(accessTokenProvider.accessToken()).thenReturn("user-token");
        when(getStudyViewerUseCase.execute(any(), eq("user-token")))
            .thenThrow(new ArchiveViewerException(ArchiveViewerException.Reason.UNAVAILABLE));

        given().when().get("/api/studies/" + VALID_STUDY_UID)
            .then().statusCode(503)
            .contentType("application/problem+json")
            .body("code", equalTo("API_ARCHIVE_UNAVAILABLE"));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("archive timeout returns 503 problem")
    void study_archive_timeout_returns_503() {
        when(accessTokenProvider.accessToken()).thenReturn("user-token");
        when(getStudyViewerUseCase.execute(any(), eq("user-token")))
            .thenThrow(new ArchiveViewerException(ArchiveViewerException.Reason.TIMEOUT));

        given().when().get("/api/studies/" + VALID_STUDY_UID)
            .then().statusCode(503)
            .contentType("application/problem+json")
            .body("code", equalTo("API_ARCHIVE_UNAVAILABLE"));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("archive connection failure returns 503 problem")
    void study_archive_connection_failure_returns_503() {
        when(accessTokenProvider.accessToken()).thenReturn("user-token");
        when(getStudyViewerUseCase.execute(any(), eq("user-token")))
            .thenThrow(new ArchiveViewerException(ArchiveViewerException.Reason.CONNECTION));

        given().when().get("/api/studies/" + VALID_STUDY_UID)
            .then().statusCode(503)
            .contentType("application/problem+json")
            .body("code", equalTo("API_ARCHIVE_UNAVAILABLE"));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("archive invalid response returns 502 problem")
    void study_archive_invalid_response_returns_502() {
        when(accessTokenProvider.accessToken()).thenReturn("user-token");
        when(getStudyViewerUseCase.execute(any(), eq("user-token")))
            .thenThrow(new ArchiveViewerException(ArchiveViewerException.Reason.INVALID_RESPONSE));

        given().when().get("/api/studies/" + VALID_STUDY_UID)
            .then().statusCode(502)
            .contentType("application/problem+json")
            .body("code", equalTo("API_ARCHIVE_RESPONSE_INVALID"));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("invalid archive metadata returns 502 problem")
    void study_invalid_archive_metadata_returns_502() {
        when(accessTokenProvider.accessToken()).thenReturn("user-token");
        when(getStudyViewerUseCase.execute(any(), eq("user-token")))
            .thenThrow(new InvalidArchiveMetadataException("Corrupt DICOM metadata"));

        given().when().get("/api/studies/" + VALID_STUDY_UID)
            .then().statusCode(502)
            .contentType("application/problem+json")
            .body("code", equalTo("API_ARCHIVE_RESPONSE_INVALID"));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("unexpected failure returns 500 without leaking details")
    void study_unexpected_failure_returns_500() {
        when(accessTokenProvider.accessToken()).thenReturn("user-token");
        when(getStudyViewerUseCase.execute(any(), eq("user-token")))
            .thenThrow(new RuntimeException("patient-secret-clinical-data"));

        Response response = given()
            .when().get("/api/studies/" + VALID_STUDY_UID)
            .then().statusCode(500)
            .contentType("application/problem+json")
            .body("code", equalTo("API_INTERNAL_ERROR"))
            .body("detail", not(containsString("patient-secret-clinical-data")))
            .body("traceId", not(blankOrNullString()))
            .extract().response();

        assertNull(response.header("X-Request-ID"));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("known study archive failure emits safe log event with route template")
    void known_study_failure_emits_safe_event_with_route_template() {
        String traceId = "8192f2b6129343ca9be36fd74be7a708";
        when(accessTokenProvider.accessToken()).thenReturn("user-token");
        when(getStudyViewerUseCase.execute(any(), eq("user-token")))
            .thenThrow(new ArchiveViewerException(
                ArchiveViewerException.Reason.TIMEOUT,
                new IllegalStateException("secret-external-cause 1.2.840.113619")));

        try (ApiFailureLogCapture logs = ApiFailureLogCapture.start(FAILURE_LOGGER)) {
            given()
                .header("traceparent", "00-" + traceId + "-7b9a559cd4474348-01")
                .when().get("/api/studies/" + VALID_STUDY_UID)
                .then().statusCode(503);

            List<LogRecord> events = logs.containing("traceId=" + traceId);
            assertEquals(1, events.size());
            assertEquals(Level.WARNING, events.getFirst().getLevel());
            String event = logs.formatted(events.getFirst());
            assertTrue(event.contains("code=API_ARCHIVE_UNAVAILABLE"));
            assertTrue(event.contains("status=503"));
            assertTrue(event.contains("method=GET"));
            assertTrue(event.contains("route=/api/studies/{studyUid}"));
            assertTrue(event.contains("reason=TIMEOUT"));
            assertFalse(event.contains(VALID_STUDY_UID));
            assertFalse(event.contains("secret-external-cause"));
            assertNull(events.getFirst().getThrown());
        }
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("invalid study UID emits safe info event with route template")
    void invalid_study_uid_emits_safe_info_event() {
        String traceId = "96f725d0d0514392b29417b5ae8d1a1b";

        try (ApiFailureLogCapture logs = ApiFailureLogCapture.start(FAILURE_LOGGER)) {
            given()
                .header("traceparent", "00-" + traceId + "-178596a0c80d4a91-01")
                .when().get("/api/studies/INVALID-STUDY-UID-LEAK")
                .then().statusCode(400);

            List<LogRecord> events = logs.containing("traceId=" + traceId);
            assertEquals(1, events.size());
            assertEquals(Level.INFO, events.getFirst().getLevel());
            String event = logs.formatted(events.getFirst());
            assertTrue(event.contains("code=API_REQUEST_INVALID"));
            assertTrue(event.contains("status=400"));
            assertTrue(event.contains("route=/api/studies/{studyUid}"));
            assertFalse(event.contains("INVALID-STUDY-UID-LEAK"));
            assertNull(events.getFirst().getThrown());
        }
    }

    // =========================================================================
    // GET /api/studies/{studyUid}/series/{seriesUid}/instances
    // =========================================================================

    @Test
    @DisplayName("anonymous series instances request receives 401 problem")
    void anonymous_instances_request_receives_401() {
        given().redirects().follow(false)
            .when().get("/api/studies/" + VALID_STUDY_UID + "/series/" + VALID_SERIES_UID + "/instances")
            .then().statusCode(401)
            .contentType("application/problem+json")
            .body("code", equalTo("API_AUTHENTICATION_REQUIRED"));
    }

    @Test
    @TestSecurity(user = "viewer-user", roles = "viewer")
    @DisplayName("user without auth role receives 403 on series instances request")
    void instances_user_without_auth_role_receives_403() {
        given()
            .when().get("/api/studies/" + VALID_STUDY_UID + "/series/" + VALID_SERIES_UID + "/instances")
            .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("valid instances request returns curated instances and calls use case once")
    void valid_instances_request_returns_curated_instances() {
        when(accessTokenProvider.accessToken()).thenReturn("user-token");
        when(getSeriesInstancesUseCase.execute(
            eq(new ViewerSeriesRef(VALID_STUDY_UID, VALID_SERIES_UID)),
            eq("user-token")
        )).thenReturn(sampleSeriesInstances());

        Response response = given()
            .when().get("/api/studies/" + VALID_STUDY_UID + "/series/" + VALID_SERIES_UID + "/instances")
            .then().statusCode(200)
            .body("studyInstanceUid", equalTo(VALID_STUDY_UID))
            .body("seriesInstanceUid", equalTo(VALID_SERIES_UID))
            .body("instances[0].sopInstanceUid", equalTo(VALID_SOP_INSTANCE_UID))
            .body("instances[0].sopClassUid", equalTo(CT_SOP_CLASS))
            .body("instances[0].instanceNumber", equalTo(1))
            .body("instances[0].rows", equalTo(512))
            .body("instances[0].columns", equalTo(512))
            .body("instances[0].samplesPerPixel", equalTo(1))
            .body("instances[0].photometricInterpretation", equalTo("MONOCHROME2"))
            .body("instances[0].bitsAllocated", equalTo(16))
            .body("instances[0].bitsStored", equalTo(12))
            .body("instances[0].highBit", equalTo(11))
            .body("instances[0].pixelRepresentation", equalTo(0))
            .body("instances[0].imagePositionPatient[0]", equalTo(-150.0f))
            .body("instances[0].imagePositionPatient[1]", equalTo(-150.0f))
            .body("instances[0].imagePositionPatient[2]", equalTo(10.0f))
            .body("instances[0].imageOrientationPatient[0]", equalTo(1.0f))
            .body("instances[0].pixelSpacing[0]", equalTo(0.5f))
            .body("instances[0].rescaleIntercept", equalTo(-1024.0f))
            .body("instances[0].rescaleSlope", equalTo(1.0f))
            .body("instances[0].windowCenter[0]", equalTo(40.0f))
            .body("instances[0].windowWidth[0]", equalTo(400.0f))
            .extract().response();

        assertNull(response.header("X-Request-ID"));
        assertNotNull(response.header("X-Trace-ID"));

        verify(getSeriesInstancesUseCase, times(1)).execute(
            eq(new ViewerSeriesRef(VALID_STUDY_UID, VALID_SERIES_UID)),
            eq("user-token")
        );
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("invalid series UID returns 400 invalid request problem")
    void invalid_series_uid_returns_400_invalid_request() {
        given()
            .when().get("/api/studies/" + VALID_STUDY_UID + "/series/not-a-series-uid/instances")
            .then().statusCode(400)
            .contentType("application/problem+json")
            .body("code", equalTo("API_REQUEST_INVALID"))
            .body("detail", equalTo("The request is invalid or malformed."));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("instances archive not found returns 404 problem")
    void instances_archive_not_found_returns_404() {
        when(accessTokenProvider.accessToken()).thenReturn("user-token");
        when(getSeriesInstancesUseCase.execute(any(), eq("user-token")))
            .thenThrow(new ArchiveViewerException(ArchiveViewerException.Reason.NOT_FOUND));

        given()
            .when().get("/api/studies/" + VALID_STUDY_UID + "/series/" + VALID_SERIES_UID + "/instances")
            .then().statusCode(404)
            .contentType("application/problem+json")
            .body("code", equalTo("API_RESOURCE_NOT_FOUND"));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("instances archive timeout returns 503 problem")
    void instances_archive_timeout_returns_503() {
        when(accessTokenProvider.accessToken()).thenReturn("user-token");
        when(getSeriesInstancesUseCase.execute(any(), eq("user-token")))
            .thenThrow(new ArchiveViewerException(ArchiveViewerException.Reason.TIMEOUT));

        given()
            .when().get("/api/studies/" + VALID_STUDY_UID + "/series/" + VALID_SERIES_UID + "/instances")
            .then().statusCode(503)
            .contentType("application/problem+json")
            .body("code", equalTo("API_ARCHIVE_UNAVAILABLE"));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("instances invalid archive metadata returns 502 problem")
    void instances_invalid_archive_metadata_returns_502() {
        when(accessTokenProvider.accessToken()).thenReturn("user-token");
        when(getSeriesInstancesUseCase.execute(any(), eq("user-token")))
            .thenThrow(new InvalidArchiveMetadataException("Corrupt instance metadata"));

        given()
            .when().get("/api/studies/" + VALID_STUDY_UID + "/series/" + VALID_SERIES_UID + "/instances")
            .then().statusCode(502)
            .contentType("application/problem+json")
            .body("code", equalTo("API_ARCHIVE_RESPONSE_INVALID"));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("instances unexpected failure returns 500 without leaking details")
    void instances_unexpected_failure_returns_500() {
        when(accessTokenProvider.accessToken()).thenReturn("user-token");
        when(getSeriesInstancesUseCase.execute(any(), eq("user-token")))
            .thenThrow(new RuntimeException("patient-secret-series-data"));

        Response response = given()
            .when().get("/api/studies/" + VALID_STUDY_UID + "/series/" + VALID_SERIES_UID + "/instances")
            .then().statusCode(500)
            .contentType("application/problem+json")
            .body("code", equalTo("API_INTERNAL_ERROR"))
            .body("detail", not(containsString("patient-secret-series-data")))
            .body("traceId", not(blankOrNullString()))
            .extract().response();

        assertNull(response.header("X-Request-ID"));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("known instances archive failure emits safe log event with route template")
    void known_instances_failure_emits_safe_event_with_route_template() {
        String traceId = "7a8192f2b6129343ca9be36fd74be7a7";
        when(accessTokenProvider.accessToken()).thenReturn("user-token");
        when(getSeriesInstancesUseCase.execute(any(), eq("user-token")))
            .thenThrow(new ArchiveViewerException(
                ArchiveViewerException.Reason.CONNECTION,
                new IllegalStateException("secret-external-cause 1.2.840.113619")));

        try (ApiFailureLogCapture logs = ApiFailureLogCapture.start(FAILURE_LOGGER)) {
            given()
                .header("traceparent", "00-" + traceId + "-7b9a559cd4474348-01")
                .when().get("/api/studies/" + VALID_STUDY_UID + "/series/" + VALID_SERIES_UID + "/instances")
                .then().statusCode(503);

            List<LogRecord> events = logs.containing("traceId=" + traceId);
            assertEquals(1, events.size());
            assertEquals(Level.WARNING, events.getFirst().getLevel());
            String event = logs.formatted(events.getFirst());
            assertTrue(event.contains("code=API_ARCHIVE_UNAVAILABLE"));
            assertTrue(event.contains("status=503"));
            assertTrue(event.contains("method=GET"));
            assertTrue(event.contains("route=/api/studies/{studyUid}/series/{seriesUid}/instances"));
            assertTrue(event.contains("reason=CONNECTION"));
            assertFalse(event.contains(VALID_STUDY_UID));
            assertFalse(event.contains(VALID_SERIES_UID));
            assertFalse(event.contains("secret-external-cause"));
            assertNull(events.getFirst().getThrown());
        }
    }
}
