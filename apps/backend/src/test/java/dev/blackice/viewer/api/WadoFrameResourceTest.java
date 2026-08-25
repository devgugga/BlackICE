package dev.blackice.viewer.api;

import dev.blackice.security.application.AccessTokenProvider;
import dev.blackice.shared.api.problem.ApiFailureLogCapture;
import dev.blackice.viewer.application.exception.ArchiveViewerException;
import dev.blackice.viewer.application.input.ViewerInstanceRef;
import dev.blackice.viewer.application.result.FrameStream;
import dev.blackice.viewer.application.usecase.RetrieveFrameUseCase;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class WadoFrameResourceTest {

    private static final String FAILURE_LOGGER = "dev.blackice.shared.api.problem.ApiFailureLogger";

    private static final String VALID_STUDY_UID = "1.2.840.113619.2.55.3.604688435.123.1599720123.467";
    private static final String VALID_SERIES_UID = "1.2.840.113619.2.55.3.604688435.123.1599720123.468";
    private static final String VALID_SOP_INSTANCE_UID = "1.2.840.113619.2.55.3.604688435.123.1599720123.469";
    private static final ViewerInstanceRef INSTANCE_REF =
        new ViewerInstanceRef(VALID_STUDY_UID, VALID_SERIES_UID, VALID_SOP_INSTANCE_UID);

    private static final String FRAME_URL = "/api/dicomweb/studies/" + VALID_STUDY_UID
        + "/series/" + VALID_SERIES_UID
        + "/instances/" + VALID_SOP_INSTANCE_UID
        + "/frames/1";

    private static final String SAMPLE_CONTENT_TYPE =
        "multipart/related; type=\"application/octet-stream\"; boundary=--frame-boundary-test";

    @InjectMock
    RetrieveFrameUseCase retrieveFrameUseCase;

    @InjectMock
    AccessTokenProvider accessTokenProvider;

    @Test
    @DisplayName("anonymous frame request receives 401 problem")
    void anonymous_frame_request_receives_401() {
        given().redirects().follow(false)
            .when().get(FRAME_URL)
            .then().statusCode(401)
            .contentType("application/problem+json")
            .body("code", equalTo("API_AUTHENTICATION_REQUIRED"));
    }

    @Test
    @TestSecurity(user = "viewer-user", roles = "viewer")
    @DisplayName("user without auth role receives 403 on frame request")
    void user_without_auth_role_receives_403() {
        given().when().get(FRAME_URL)
            .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("valid frame request streams raw multipart bytes and sets headers")
    void valid_frame_request_streams_raw_multipart_bytes() {
        byte[] rawBytes = ("--frame-boundary-test\r\n"
            + "Content-Type: application/octet-stream\r\n\r\n"
            + "PIXEL_DATA_PAYLOAD_12345\r\n"
            + "--frame-boundary-test--\r\n").getBytes(StandardCharsets.UTF_8);

        when(accessTokenProvider.accessToken()).thenReturn("user-token");
        when(retrieveFrameUseCase.execute(eq(INSTANCE_REF), eq("user-token")))
            .thenReturn(new FrameStream(SAMPLE_CONTENT_TYPE, new ByteArrayInputStream(rawBytes)));

        Response response = given()
            .when().get(FRAME_URL)
            .then().statusCode(200)
            .header("Cache-Control", equalTo("private, no-store"))
            .header("Content-Type", equalTo(SAMPLE_CONTENT_TYPE))
            .extract().response();

        assertNull(response.header("X-Request-ID"));
        assertNotNull(response.header("X-Trace-ID"));
        assertArrayEquals(rawBytes, response.asByteArray());

        verify(retrieveFrameUseCase, times(1)).execute(eq(INSTANCE_REF), eq("user-token"));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("invalid study UID returns 400 invalid request problem")
    void invalid_study_uid_returns_400_invalid_request() {
        String badUrl = "/api/dicomweb/studies/invalid-study-uid/series/"
            + VALID_SERIES_UID + "/instances/" + VALID_SOP_INSTANCE_UID + "/frames/1";

        Response response = given()
            .when().get(badUrl)
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
    @DisplayName("invalid series UID returns 400 invalid request problem")
    void invalid_series_uid_returns_400_invalid_request() {
        String badUrl = "/api/dicomweb/studies/" + VALID_STUDY_UID
            + "/series/invalid-series-uid/instances/" + VALID_SOP_INSTANCE_UID + "/frames/1";

        given()
            .when().get(badUrl)
            .then().statusCode(400)
            .contentType("application/problem+json")
            .body("code", equalTo("API_REQUEST_INVALID"));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("invalid SOP UID returns 400 invalid request problem")
    void invalid_sop_instance_uid_returns_400_invalid_request() {
        String badUrl = "/api/dicomweb/studies/" + VALID_STUDY_UID
            + "/series/" + VALID_SERIES_UID + "/instances/invalid-sop-uid/frames/1";

        given()
            .when().get(badUrl)
            .then().statusCode(400)
            .contentType("application/problem+json")
            .body("code", equalTo("API_REQUEST_INVALID"));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("archive authentication failure returns 401 problem")
    void frame_archive_authentication_failure_returns_401() {
        when(accessTokenProvider.accessToken()).thenReturn("user-token");
        when(retrieveFrameUseCase.execute(any(), eq("user-token")))
            .thenThrow(new ArchiveViewerException(ArchiveViewerException.Reason.AUTHENTICATION));

        given().when().get(FRAME_URL)
            .then().statusCode(401)
            .contentType("application/problem+json")
            .body("code", equalTo("API_AUTHENTICATION_REQUIRED"));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("archive access denied returns 403 problem")
    void frame_archive_access_denied_returns_403() {
        when(accessTokenProvider.accessToken()).thenReturn("user-token");
        when(retrieveFrameUseCase.execute(any(), eq("user-token")))
            .thenThrow(new ArchiveViewerException(ArchiveViewerException.Reason.ACCESS_DENIED));

        given().when().get(FRAME_URL)
            .then().statusCode(403)
            .contentType("application/problem+json")
            .body("code", equalTo("API_ACCESS_DENIED"));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("archive not found returns 404 problem")
    void frame_archive_not_found_returns_404() {
        when(accessTokenProvider.accessToken()).thenReturn("user-token");
        when(retrieveFrameUseCase.execute(any(), eq("user-token")))
            .thenThrow(new ArchiveViewerException(ArchiveViewerException.Reason.NOT_FOUND));

        given().when().get(FRAME_URL)
            .then().statusCode(404)
            .contentType("application/problem+json")
            .body("code", equalTo("API_RESOURCE_NOT_FOUND"));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("archive unavailable returns 503 problem")
    void frame_archive_unavailable_returns_503() {
        when(accessTokenProvider.accessToken()).thenReturn("user-token");
        when(retrieveFrameUseCase.execute(any(), eq("user-token")))
            .thenThrow(new ArchiveViewerException(ArchiveViewerException.Reason.UNAVAILABLE));

        given().when().get(FRAME_URL)
            .then().statusCode(503)
            .contentType("application/problem+json")
            .body("code", equalTo("API_ARCHIVE_UNAVAILABLE"));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("archive timeout returns 503 problem")
    void frame_archive_timeout_returns_503() {
        when(accessTokenProvider.accessToken()).thenReturn("user-token");
        when(retrieveFrameUseCase.execute(any(), eq("user-token")))
            .thenThrow(new ArchiveViewerException(ArchiveViewerException.Reason.TIMEOUT));

        given().when().get(FRAME_URL)
            .then().statusCode(503)
            .contentType("application/problem+json")
            .body("code", equalTo("API_ARCHIVE_UNAVAILABLE"));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("archive connection failure returns 503 problem")
    void frame_archive_connection_failure_returns_503() {
        when(accessTokenProvider.accessToken()).thenReturn("user-token");
        when(retrieveFrameUseCase.execute(any(), eq("user-token")))
            .thenThrow(new ArchiveViewerException(ArchiveViewerException.Reason.CONNECTION));

        given().when().get(FRAME_URL)
            .then().statusCode(503)
            .contentType("application/problem+json")
            .body("code", equalTo("API_ARCHIVE_UNAVAILABLE"));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("archive invalid response returns 502 problem")
    void frame_archive_invalid_response_returns_502() {
        when(accessTokenProvider.accessToken()).thenReturn("user-token");
        when(retrieveFrameUseCase.execute(any(), eq("user-token")))
            .thenThrow(new ArchiveViewerException(ArchiveViewerException.Reason.INVALID_RESPONSE));

        given().when().get(FRAME_URL)
            .then().statusCode(502)
            .contentType("application/problem+json")
            .body("code", equalTo("API_ARCHIVE_RESPONSE_INVALID"));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("unexpected failure returns 500 without leaking details")
    void frame_unexpected_failure_returns_500() {
        when(accessTokenProvider.accessToken()).thenReturn("user-token");
        when(retrieveFrameUseCase.execute(any(), eq("user-token")))
            .thenThrow(new RuntimeException("secret-pixel-data-leak"));

        Response response = given()
            .when().get(FRAME_URL)
            .then().statusCode(500)
            .contentType("application/problem+json")
            .body("code", equalTo("API_INTERNAL_ERROR"))
            .body("detail", not(containsString("secret-pixel-data-leak")))
            .body("traceId", not(blankOrNullString()))
            .extract().response();

        assertNull(response.header("X-Request-ID"));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("known frame archive failure emits safe log event with route template")
    void known_frame_failure_emits_safe_event_with_route_template() {
        String traceId = "1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d";
        when(accessTokenProvider.accessToken()).thenReturn("user-token");
        when(retrieveFrameUseCase.execute(any(), eq("user-token")))
            .thenThrow(new ArchiveViewerException(
                ArchiveViewerException.Reason.TIMEOUT,
                new IllegalStateException("secret-external-cause " + VALID_SOP_INSTANCE_UID)));

        try (ApiFailureLogCapture logs = ApiFailureLogCapture.start(FAILURE_LOGGER)) {
            given()
                .header("traceparent", "00-" + traceId + "-7b9a559cd4474348-01")
                .when().get(FRAME_URL)
                .then().statusCode(503);

            List<LogRecord> events = logs.containing("traceId=" + traceId);
            assertEquals(1, events.size());
            assertEquals(Level.WARNING, events.getFirst().getLevel());
            String event = logs.formatted(events.getFirst());
            assertTrue(event.contains("code=API_ARCHIVE_UNAVAILABLE"));
            assertTrue(event.contains("status=503"));
            assertTrue(event.contains("method=GET"));
            assertTrue(event.contains("route=/api/dicomweb/studies/{studyUid}/series/{seriesUid}/instances/{sopUid}/frames/1"));
            assertTrue(event.contains("reason=TIMEOUT"));
            assertFalse(event.contains(VALID_STUDY_UID));
            assertFalse(event.contains(VALID_SERIES_UID));
            assertFalse(event.contains(VALID_SOP_INSTANCE_UID));
            assertFalse(event.contains("secret-external-cause"));
            assertNull(events.getFirst().getThrown());
        }
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("invalid frame UID emits safe info event with route template")
    void invalid_frame_uid_emits_safe_info_event() {
        String traceId = "9876543210abcdef0123456789abcdef";
        String badUrl = "/api/dicomweb/studies/INVALID_STUDY/series/"
            + VALID_SERIES_UID + "/instances/" + VALID_SOP_INSTANCE_UID + "/frames/1";

        try (ApiFailureLogCapture logs = ApiFailureLogCapture.start(FAILURE_LOGGER)) {
            given()
                .header("traceparent", "00-" + traceId + "-178596a0c80d4a91-01")
                .when().get(badUrl)
                .then().statusCode(400);

            List<LogRecord> events = logs.containing("traceId=" + traceId);
            assertEquals(1, events.size());
            assertEquals(Level.INFO, events.getFirst().getLevel());
            String event = logs.formatted(events.getFirst());
            assertTrue(event.contains("code=API_REQUEST_INVALID"));
            assertTrue(event.contains("status=400"));
            assertTrue(event.contains("route=/api/dicomweb/studies/{studyUid}/series/{seriesUid}/instances/{sopUid}/frames/1"));
            assertFalse(event.contains("INVALID_STUDY"));
            assertNull(events.getFirst().getThrown());
        }
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    @DisplayName("body throwing after first chunk is not replaced by Problem Details")
    void body_throwing_after_first_chunk_is_not_replaced_by_problem_details() {
        int chunkCommitSize = 64 * 1024;
        InputStream throwingStream = new InputStream() {
            private int bytesRead = 0;

            @Override
            public int read() throws IOException {
                if (bytesRead++ < chunkCommitSize) {
                    return 'A';
                }
                throw new IOException("Simulated network stream break mid-transfer");
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if (bytesRead >= chunkCommitSize) {
                    throw new IOException("Simulated network stream break mid-transfer");
                }
                int toRead = Math.min(len, chunkCommitSize - bytesRead);
                for (int i = 0; i < toRead; i++) {
                    b[off + i] = 'A';
                }
                bytesRead += toRead;
                return toRead;
            }
        };

        when(accessTokenProvider.accessToken()).thenReturn("user-token");
        when(retrieveFrameUseCase.execute(eq(INSTANCE_REF), eq("user-token")))
            .thenReturn(new FrameStream(SAMPLE_CONTENT_TYPE, throwingStream));

        try {
            Response response = given()
                .when().get(FRAME_URL)
                .andReturn();

            String body = response.getBody().asString();
            assertFalse(body.contains("\"code\":\"API_"));
        } catch (Exception expected) {
            // Broken connection or stream termination as expected
            assertTrue(expected.getMessage() != null || expected instanceof IOException);
        }
    }
}
