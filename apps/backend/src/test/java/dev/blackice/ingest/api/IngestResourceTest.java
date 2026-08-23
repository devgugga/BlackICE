package dev.blackice.ingest.api;

import dev.blackice.ingest.application.input.UploadedDicom;
import dev.blackice.ingest.application.result.IngestResult;
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

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    void anonymous_request_receives_authentication_challenge() {
        given().redirects().follow(false)
            .multiPart("files", "test.dcm", new byte[] {1, 2, 3}, "application/dicom")
            .when().post("/api/studies")
            .then().statusCode(302);
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
    void request_without_csrf_header_receives_400() {
        String csrf = getCsrfToken();
        given()
            .cookie("csrf-token", csrf)
            .multiPart("files", "test.dcm", new byte[] {1, 2, 3}, "application/dicom")
            .when().post("/api/studies")
            .then().statusCode(400);
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

        String requestId = response.header("X-Request-ID");
        assertNotNull(requestId);
        assertDoesNotThrow(() -> UUID.fromString(requestId));
        verify(useCase).ingest(anyList(), eq("test-token"));
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
            .then().statusCode(400);
    }

    @Test
    void upload_of_501_mocked_files_returns_413_without_calling_use_case() {
        IngestStudiesUseCase mockUseCase = mock(IngestStudiesUseCase.class);
        AccessTokenProvider mockToken = mock(AccessTokenProvider.class);
        IngestResource resource = new IngestResource(mockUseCase, mockToken, 500, 524288000L);

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
        IngestResource resource = new IngestResource(mockUseCase, mockToken, 500, 524288000L);

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
        IngestResult emptyResponse = new IngestResult(
            IngestResult.Outcome.COMPLETE,
            new IngestResult.Summary(1, 1, 0, 1, 0),
            List.of(),
            List.of()
        );

        when(useCase.ingest(anyList(), anyString())).thenAnswer(invocation -> {
            List<UploadedDicom> uploads = invocation.getArgument(0);
            Path path = uploads.get(0).path();
            uploadedTempPath.set(path);
            assertTrue(Files.exists(path), "The temporary file must exist while the use case is running");
            return emptyResponse;
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
