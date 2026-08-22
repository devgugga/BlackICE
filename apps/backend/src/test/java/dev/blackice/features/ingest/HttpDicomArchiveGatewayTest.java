package dev.blackice.features.ingest;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpDicomArchiveGatewayTest {

    @TempDir
    Path tempDir;

    private HttpServer server;
    private int port;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        port = server.getAddress().getPort();
        baseUrl = "http://127.0.0.1:" + port + "/dcm4chee-arc/aets/DCM4CHEE/rs";
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void envia_token_e_multipart_related_para_o_estudo() throws Exception {
        AtomicReference<Headers> capturedHeaders = new AtomicReference<>();
        AtomicReference<String> capturedPath = new AtomicReference<>();
        AtomicReference<String> capturedMethod = new AtomicReference<>();
        ByteArrayOutputStream capturedBody = new ByteArrayOutputStream();

        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies/1.2.840.10008.1", exchange -> {
            capturedMethod.set(exchange.getRequestMethod());
            capturedPath.set(exchange.getRequestURI().getPath());
            capturedHeaders.set(exchange.getRequestHeaders());

            try (InputStream in = exchange.getRequestBody()) {
                in.transferTo(capturedBody);
            }

            byte[] response = """
                {"00081199":{"vr":"SQ","Value":[
                  {"00081155":{"vr":"UI","Value":["1.2.840.10008.1.1.1"]}},
                  {"00081155":{"vr":"UI","Value":["1.2.840.10008.1.1.2"]}}
                ]}}
                """.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "application/dicom+json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(response);
            }
            exchange.close();
        });

        Path file1 = createDicomFile("1.2.840.10008.1", "1.2.840.10008.1.1", "1.2.840.10008.1.1.1", (byte) 42);
        Path file2 = createDicomFile("1.2.840.10008.1", "1.2.840.10008.1.1", "1.2.840.10008.1.1.2", (byte) 43);

        List<ValidatedDicom> files = List.of(
            new ValidatedDicom(file1, "file1.dcm", Files.size(file1), "1.2.840.10008.1", "1.2.840.10008.1.1", "1.2.840.10008.1.1.1", UID.SecondaryCaptureImageStorage, "hash1"),
            new ValidatedDicom(file2, "file2.dcm", Files.size(file2), "1.2.840.10008.1", "1.2.840.10008.1.1", "1.2.840.10008.1.1.2", UID.SecondaryCaptureImageStorage, "hash2")
        );

        HttpDicomArchiveGateway gateway = new HttpDicomArchiveGateway(
            baseUrl,
            Duration.ofSeconds(5),
            new StowResponseParser(),
            HttpClient.newHttpClient()
        );

        StowStudyResult result = gateway.storeStudy("1.2.840.10008.1", files, "test-user-token");

        assertEquals("POST", capturedMethod.get());
        assertEquals("/dcm4chee-arc/aets/DCM4CHEE/rs/studies/1.2.840.10008.1", capturedPath.get());
        assertEquals("Bearer test-user-token", capturedHeaders.get().getFirst("Authorization"));
        assertEquals("application/dicom+json", capturedHeaders.get().getFirst("Accept"));

        String contentType = capturedHeaders.get().getFirst("Content-Type");
        assertNotNull(contentType);
        assertTrue(contentType.startsWith("multipart/related; type=\"application/dicom\"; boundary="));

        String bodyString = capturedBody.toString(StandardCharsets.ISO_8859_1);
        // Header de parte deve conter Content-Type: application/dicom e NÃO Content-Disposition
        assertTrue(bodyString.contains("Content-Type: application/dicom\r\n\r\n"));
        assertFalse(bodyString.contains("Content-Disposition"));

        // Resultado interpretado com sucesso
        assertEquals("1.2.840.10008.1", result.studyInstanceUid());
        assertEquals(2, result.instances().size());
        assertEquals(StowInstanceResult.Status.ACCEPTED, result.instances().get(0).status());
        assertEquals(StowInstanceResult.Status.ACCEPTED, result.instances().get(1).status());
    }

    @Test
    void resposta_nao_2xx_lanca_archive_unavailable_sem_vazar_body() throws Exception {
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies/1.2.3", exchange -> {
            exchange.getRequestBody().transferTo(OutputStream.nullOutputStream());
            byte[] response = "{\"error\":\"secret-patient-data-that-must-not-leak\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        Path file = createDicomFile("1.2.3", "1.2.3.1", "1.2.3.1.1", (byte) 1);
        List<ValidatedDicom> files = List.of(
            new ValidatedDicom(file, "f.dcm", Files.size(file), "1.2.3", "1.2.3.1", "1.2.3.1.1", UID.SecondaryCaptureImageStorage, "h")
        );

        HttpDicomArchiveGateway gateway = new HttpDicomArchiveGateway(
            baseUrl,
            Duration.ofSeconds(5),
            new StowResponseParser(),
            HttpClient.newHttpClient()
        );

        ArchiveUnavailableException ex = assertThrows(
            ArchiveUnavailableException.class,
            () -> gateway.storeStudy("1.2.3", files, "secret-token")
        );

        assertEquals(ArchiveUnavailableException.Reason.HTTP_STATUS, ex.reason());
        assertEquals("HTTP_STATUS", ex.getMessage());
        assertFalse(ex.getMessage().contains("secret"));
    }

    @Test
    void resposta_200_com_json_invalido_lanca_http_status() throws Exception {
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies/1.2.3", exchange -> {
            exchange.getRequestBody().transferTo(OutputStream.nullOutputStream());
            byte[] response = "corrupted-non-json-content".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        Path file = createDicomFile("1.2.3", "1.2.3.1", "1.2.3.1.1", (byte) 1);
        List<ValidatedDicom> files = List.of(
            new ValidatedDicom(file, "f.dcm", Files.size(file), "1.2.3", "1.2.3.1", "1.2.3.1.1", UID.SecondaryCaptureImageStorage, "h")
        );

        HttpDicomArchiveGateway gateway = new HttpDicomArchiveGateway(
            baseUrl,
            Duration.ofSeconds(5),
            new StowResponseParser(),
            HttpClient.newHttpClient()
        );

        ArchiveUnavailableException ex = assertThrows(
            ArchiveUnavailableException.class,
            () -> gateway.storeStudy("1.2.3", files, "token")
        );

        assertEquals(ArchiveUnavailableException.Reason.HTTP_STATUS, ex.reason());
    }

    @Test
    void timeout_lanca_archive_unavailable_com_reason_timeout() throws Exception {
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies/1.2.3", exchange -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {}
            exchange.getRequestBody().transferTo(OutputStream.nullOutputStream());
            byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        Path file = createDicomFile("1.2.3", "1.2.3.1", "1.2.3.1.1", (byte) 1);
        List<ValidatedDicom> files = List.of(
            new ValidatedDicom(file, "f.dcm", Files.size(file), "1.2.3", "1.2.3.1", "1.2.3.1.1", UID.SecondaryCaptureImageStorage, "h")
        );

        HttpDicomArchiveGateway gateway = new HttpDicomArchiveGateway(
            baseUrl,
            Duration.ofMillis(50),
            new StowResponseParser(),
            HttpClient.newHttpClient()
        );

        ArchiveUnavailableException ex = assertThrows(
            ArchiveUnavailableException.class,
            () -> gateway.storeStudy("1.2.3", files, "token")
        );

        assertEquals(ArchiveUnavailableException.Reason.TIMEOUT, ex.reason());
    }

    @Test
    void falha_de_conexao_lanca_archive_unavailable_com_reason_connection() throws Exception {
        // Encerra o servidor para simular serviço offline
        server.stop(0);

        Path file = createDicomFile("1.2.3", "1.2.3.1", "1.2.3.1.1", (byte) 1);
        List<ValidatedDicom> files = List.of(
            new ValidatedDicom(file, "f.dcm", Files.size(file), "1.2.3", "1.2.3.1", "1.2.3.1.1", UID.SecondaryCaptureImageStorage, "h")
        );

        HttpDicomArchiveGateway gateway = new HttpDicomArchiveGateway(
            baseUrl,
            Duration.ofSeconds(2),
            new StowResponseParser(),
            HttpClient.newHttpClient()
        );

        ArchiveUnavailableException ex = assertThrows(
            ArchiveUnavailableException.class,
            () -> gateway.storeStudy("1.2.3", files, "token")
        );

        assertEquals(ArchiveUnavailableException.Reason.CONNECTION, ex.reason());
    }

    @Test
    void arquivo_inexistente_lanca_connection() {
        Path nonexistent = tempDir.resolve("missing.dcm");
        List<ValidatedDicom> files = List.of(
            new ValidatedDicom(nonexistent, "missing.dcm", 100, "1.2.3", "1.2.3.1", "1.2.3.1.1", UID.SecondaryCaptureImageStorage, "h")
        );

        HttpDicomArchiveGateway gateway = new HttpDicomArchiveGateway(
            baseUrl,
            Duration.ofSeconds(2),
            new StowResponseParser(),
            HttpClient.newHttpClient()
        );

        ArchiveUnavailableException ex = assertThrows(
            ArchiveUnavailableException.class,
            () -> gateway.storeStudy("1.2.3", files, "token")
        );

        assertEquals(ArchiveUnavailableException.Reason.CONNECTION, ex.reason());
    }

    @Test
    void multipart_related_body_publisher_formato_e_delimitadores() throws Exception {
        Path file1 = createDicomFile("1.2.3", "1.2.3.1", "1.2.3.1.1", (byte) 10);
        byte[] fileBytes = Files.readAllBytes(file1);

        List<ValidatedDicom> files = List.of(
            new ValidatedDicom(file1, "f1.dcm", fileBytes.length, "1.2.3", "1.2.3.1", "1.2.3.1.1", UID.SecondaryCaptureImageStorage, "h")
        );

        String boundary = "test-boundary-123";
        HttpRequest.BodyPublisher publisher = MultipartRelatedBodyPublisher.publish(files, boundary);

        ByteArrayOutputStream collected = new ByteArrayOutputStream();
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] b = new byte[item.remaining()];
                item.get(b);
                collected.writeBytes(b);
            }

            @Override
            public void onError(Throwable throwable) {}

            @Override
            public void onComplete() {}
        });

        byte[] allBytes = collected.toByteArray();
        String expectedHeader = "--test-boundary-123\r\nContent-Type: application/dicom\r\n\r\n";
        String expectedClosing = "\r\n--test-boundary-123--\r\n";

        String textPrefix = new String(allBytes, 0, expectedHeader.length(), StandardCharsets.US_ASCII);
        assertEquals(expectedHeader, textPrefix);

        String textSuffix = new String(allBytes, allBytes.length - expectedClosing.length(), expectedClosing.length(), StandardCharsets.US_ASCII);
        assertEquals(expectedClosing, textSuffix);
    }

    private Path createDicomFile(String studyUid, String seriesUid, String sopUid, byte pixel) throws IOException {
        Attributes ds = new Attributes();
        ds.setString(Tag.SOPClassUID, VR.UI, UID.SecondaryCaptureImageStorage);
        ds.setString(Tag.SOPInstanceUID, VR.UI, sopUid);
        ds.setString(Tag.StudyInstanceUID, VR.UI, studyUid);
        ds.setString(Tag.SeriesInstanceUID, VR.UI, seriesUid);
        ds.setString(Tag.Modality, VR.CS, "OT");
        ds.setInt(Tag.Rows, VR.US, 1);
        ds.setInt(Tag.Columns, VR.US, 1);
        ds.setInt(Tag.SamplesPerPixel, VR.US, 1);
        ds.setString(Tag.PhotometricInterpretation, VR.CS, "MONOCHROME2");
        ds.setInt(Tag.BitsAllocated, VR.US, 8);
        ds.setInt(Tag.BitsStored, VR.US, 8);
        ds.setInt(Tag.HighBit, VR.US, 7);
        ds.setInt(Tag.PixelRepresentation, VR.US, 0);
        ds.setBytes(Tag.PixelData, VR.OB, new byte[] {pixel, 0});

        Path path = tempDir.resolve(sopUid + ".dcm");
        try (DicomOutputStream out = new DicomOutputStream(path.toFile())) {
            out.writeDataset(ds.createFileMetaInformation(UID.ExplicitVRLittleEndian), ds);
        }
        return path;
    }
}
