package dev.blackice.features.ingest;

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
    IngestService service;

    @InjectMock
    CurrentAccessToken accessToken;

    private String getCsrfToken() {
        return given()
            .when().get("/api/csrf")
            .then().statusCode(204)
            .extract().cookie("csrf-token");
    }

    @Test
    void anonimo_recebe_desafio_de_autenticacao() {
        given().redirects().follow(false)
            .multiPart("files", "test.dcm", new byte[] {1, 2, 3}, "application/dicom")
            .when().post("/api/studies")
            .then().statusCode(302);
    }

    @Test
    @TestSecurity(user = "user", roles = "viewer")
    void usuario_sem_role_auth_recebe_403() {
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
    void requisicao_sem_header_csrf_recebe_400() {
        String csrf = getCsrfToken();
        given()
            .cookie("csrf-token", csrf)
            .multiPart("files", "test.dcm", new byte[] {1, 2, 3}, "application/dicom")
            .when().post("/api/studies")
            .then().statusCode(400);
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    void requisicao_com_cookie_e_header_csrf_chama_service_e_retorna_sucesso() {
        String csrf = getCsrfToken();
        when(accessToken.value()).thenReturn("test-token");

        IngestResponse.Summary summary = new IngestResponse.Summary(1, 1, 0, 1, 0);
        IngestResponse.StudyResult studyResult = new IngestResponse.StudyResult(
            "1.2.3",
            IngestResponse.StudyStatus.COMPLETE,
            List.of(new IngestResponse.InstanceResult("1.2.3.1", StowInstanceResult.Status.ACCEPTED, null)),
            null
        );
        IngestResponse responseBody = new IngestResponse(
            IngestResponse.Outcome.COMPLETE,
            summary,
            List.of(studyResult),
            List.of()
        );
        when(service.ingest(anyList(), eq("test-token"))).thenReturn(new IngestExecution(200, responseBody));

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
        verify(service).ingest(anyList(), eq("test-token"));
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    void upload_sem_arquivos_retorna_400() {
        String csrf = getCsrfToken();
        given()
            .cookie("csrf-token", csrf)
            .header("X-CSRF-TOKEN", csrf)
            .multiPart("dummy", "empty")
            .when().post("/api/studies")
            .then().statusCode(400);
    }

    @Test
    void limite_de_501_arquivos_mockados_rejeita_com_413_sem_alocar_body_e_sem_chamar_service() {
        IngestService mockService = mock(IngestService.class);
        CurrentAccessToken mockToken = mock(CurrentAccessToken.class);
        IngestResource resource = new IngestResource(mockService, mockToken, 500, 524288000L);

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
        verify(mockService, never()).ingest(anyList(), anyString());
    }

    @Test
    void limite_de_tamanho_mockado_acima_de_500mb_rejeita_com_413_sem_alocar_body_e_sem_chamar_service() {
        IngestService mockService = mock(IngestService.class);
        CurrentAccessToken mockToken = mock(CurrentAccessToken.class);
        IngestResource resource = new IngestResource(mockService, mockToken, 500, 524288000L);

        FileUpload file = mock(FileUpload.class);
        when(file.size()).thenReturn(524288001L);
        when(file.fileName()).thenReturn("huge.dcm");
        when(file.uploadedFile()).thenReturn(Path.of("/tmp/huge.dcm"));

        jakarta.ws.rs.core.Response response = resource.ingest(List.of(file));
        assertEquals(Status.REQUEST_ENTITY_TOO_LARGE.getStatusCode(), response.getStatus());
        verify(mockService, never()).ingest(anyList(), anyString());
    }

    @Test
    @TestSecurity(user = "dr.teste", roles = "auth")
    void arquivo_temporario_gerenciado_pelo_framework_deixa_de_existir_apos_requisicao() {
        String csrf = getCsrfToken();
        when(accessToken.value()).thenReturn("test-token");

        AtomicReference<Path> uploadedTempPath = new AtomicReference<>();
        IngestResponse emptyResponse = new IngestResponse(
            IngestResponse.Outcome.COMPLETE,
            new IngestResponse.Summary(1, 1, 0, 1, 0),
            List.of(),
            List.of()
        );

        when(service.ingest(anyList(), anyString())).thenAnswer(invocation -> {
            List<UploadedDicom> uploads = invocation.getArgument(0);
            Path path = uploads.get(0).path();
            uploadedTempPath.set(path);
            assertTrue(Files.exists(path), "O arquivo temporário deve existir durante a execução do serviço");
            return new IngestExecution(200, emptyResponse);
        });

        given()
            .cookie("csrf-token", csrf)
            .header("X-CSRF-TOKEN", csrf)
            .multiPart("files", "temp-test.dcm", new byte[] {1, 2, 3, 4}, "application/dicom")
            .when().post("/api/studies")
            .then().statusCode(200);

        assertNotNull(uploadedTempPath.get(), "O caminho temporário deve ter sido capturado");
        assertFalse(Files.exists(uploadedTempPath.get()), "O arquivo temporário deve ser removido pelo Quarkus ao fim da requisição");
    }
}
