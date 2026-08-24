package dev.blackice.ingest.infrastructure.dicomweb;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import dev.blackice.ingest.application.exception.ArchiveUnavailableException;
import dev.blackice.ingest.application.result.StowInstanceResult;
import dev.blackice.ingest.application.result.StowStudyResult;
import dev.blackice.ingest.application.validation.ValidatedDicom;
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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
    void an_unexpected_bug_is_not_disguised_as_a_connection_failure() throws Exception {
        Path file = createDicomFile("1.2.3", "1.2.3.1", "1.2.3.1.1", (byte) 9);
        List<ValidatedDicom> files = List.of(new ValidatedDicom(
            0, file, "f.dcm", Files.size(file), "1.2.3", "1.2.3.1", "1.2.3.1.1",
            UID.SecondaryCaptureImageStorage, "h"));

        HttpClient broken = org.mockito.Mockito.mock(HttpClient.class);
        org.mockito.Mockito.when(broken.send(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenThrow(new IllegalStateException("bug interno"));

        HttpDicomArchiveGateway gateway = new HttpDicomArchiveGateway(
            baseUrl, Duration.ofSeconds(5), new StowResponseParser(), broken);

        assertThrows(IllegalStateException.class,
            () -> gateway.storeStudy("1.2.3", files, "test-user-token"));
    }

    @Test
    void sends_token_and_multipart_related_body_for_study() throws Exception {
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

            exchange.getResponseHeaders().set("Content-Type", "Application/Dicom+Json; charset=UTF-8");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(response);
            }
            exchange.close();
        });

        Path file1 = createDicomFile("1.2.840.10008.1", "1.2.840.10008.1.1", "1.2.840.10008.1.1.1", (byte) 42);
        Path file2 = createDicomFile("1.2.840.10008.1", "1.2.840.10008.1.1", "1.2.840.10008.1.1.2", (byte) 43);

        List<ValidatedDicom> files = List.of(
            new ValidatedDicom(0, file1, "file1.dcm", Files.size(file1), "1.2.840.10008.1", "1.2.840.10008.1.1", "1.2.840.10008.1.1.1", UID.SecondaryCaptureImageStorage, "hash1"),
            new ValidatedDicom(0, file2, "file2.dcm", Files.size(file2), "1.2.840.10008.1", "1.2.840.10008.1.1", "1.2.840.10008.1.1.2", UID.SecondaryCaptureImageStorage, "hash2")
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
        // Each part must carry application/dicom without a Content-Disposition header.
        assertTrue(bodyString.contains("Content-Type: application/dicom\r\n\r\n"));
        assertFalse(bodyString.contains("Content-Disposition"));

        // The returned STOW payload is parsed as accepted.
        assertEquals("1.2.840.10008.1", result.studyInstanceUid());
        assertEquals(2, result.instances().size());
        assertEquals(StowInstanceResult.Status.ACCEPTED, result.instances().get(0).status());
        assertEquals(StowInstanceResult.Status.ACCEPTED, result.instances().get(1).status());
    }

    @Test
    void non_2xx_response_throws_archive_unavailable_without_leaking_body() throws Exception {
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies/1.2.3", exchange -> {
            exchange.getRequestBody().transferTo(OutputStream.nullOutputStream());
            byte[] response = "{\"error\":\"secret-patient-data-that-must-not-leak\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        Path file = createDicomFile("1.2.3", "1.2.3.1", "1.2.3.1.1", (byte) 1);
        List<ValidatedDicom> files = List.of(
            new ValidatedDicom(0, file, "f.dcm", Files.size(file), "1.2.3", "1.2.3.1", "1.2.3.1.1", UID.SecondaryCaptureImageStorage, "h")
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
    void response_200_with_invalid_json_is_reported_as_invalid_response_not_unavailability() throws Exception {
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies/1.2.3", exchange -> {
            exchange.getRequestBody().transferTo(OutputStream.nullOutputStream());
            byte[] response = "corrupted-non-json-content".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        Path file = createDicomFile("1.2.3", "1.2.3.1", "1.2.3.1.1", (byte) 1);
        List<ValidatedDicom> files = List.of(
            new ValidatedDicom(0, file, "f.dcm", Files.size(file), "1.2.3", "1.2.3.1", "1.2.3.1.1", UID.SecondaryCaptureImageStorage, "h")
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

        // A 2xx response cannot prove the archive did not store the submitted instance.
        assertEquals("OUTCOME_UNKNOWN", ex.reason().name());
    }

    @Test
    void missing_content_type_after_2xx_is_outcome_unknown() throws Exception {
        respondToStore(200, null, validStowBody("1.2.3.1"));

        ArchiveUnavailableException ex = assertThrows(ArchiveUnavailableException.class,
            () -> gateway(HttpClient.newHttpClient(), new StowResponseParser())
                .storeStudy("1.2.3", oneValidatedFile(), "token"));

        assertEquals("OUTCOME_UNKNOWN", ex.reason().name());
    }

    @Test
    void wrong_content_type_after_2xx_is_outcome_unknown() throws Exception {
        respondToStore(200, "application/json", validStowBody("1.2.3.1"));

        ArchiveUnavailableException ex = assertThrows(ArchiveUnavailableException.class,
            () -> gateway(HttpClient.newHttpClient(), new StowResponseParser())
                .storeStudy("1.2.3", oneValidatedFile(), "token"));

        assertEquals("OUTCOME_UNKNOWN", ex.reason().name());
    }

    @Test
    void unexpected_stow_parser_runtime_failure_propagates() throws Exception {
        String responseBody = validStowBody("1.2.3.1");
        respondToStore(200, "application/dicom+json", responseBody);
        StowResponseParser brokenParser = mock(StowResponseParser.class);
        when(brokenParser.parse(anyString(), anyString(), anySet()))
            .thenThrow(new IllegalStateException("unexpected parser bug"));

        assertThrows(IllegalStateException.class,
            () -> gateway(HttpClient.newHttpClient(), brokenParser)
                .storeStudy("1.2.3", oneValidatedFile(), "token"));
    }

    @Test
    void connect_timeout_before_body_subscription_remains_timeout() throws Exception {
        HttpClient client = throwingClient(new HttpConnectTimeoutException("connect timeout"), false);

        ArchiveUnavailableException ex = assertThrows(ArchiveUnavailableException.class,
            () -> gateway(client, new StowResponseParser())
                .storeStudy("1.2.3", oneValidatedFile(), "token"));

        assertEquals(ArchiveUnavailableException.Reason.TIMEOUT, ex.reason());
    }

    @Test
    void connect_failure_before_body_subscription_remains_connection() throws Exception {
        HttpClient client = throwingClient(new ConnectException("connect failure"), false);

        ArchiveUnavailableException ex = assertThrows(ArchiveUnavailableException.class,
            () -> gateway(client, new StowResponseParser())
                .storeStudy("1.2.3", oneValidatedFile(), "token"));

        assertEquals(ArchiveUnavailableException.Reason.CONNECTION, ex.reason());
    }

    @Test
    void timeout_after_body_subscription_is_outcome_unknown() throws Exception {
        HttpClient client = throwingClient(new HttpTimeoutException("request timeout"), true);

        ArchiveUnavailableException ex = assertThrows(ArchiveUnavailableException.class,
            () -> gateway(client, new StowResponseParser())
                .storeStudy("1.2.3", oneValidatedFile(), "token"));

        assertEquals("OUTCOME_UNKNOWN", ex.reason().name());
    }

    @Test
    void connection_reset_after_body_subscription_is_outcome_unknown() throws Exception {
        HttpClient client = throwingClient(new IOException("connection reset"), true);

        ArchiveUnavailableException ex = assertThrows(ArchiveUnavailableException.class,
            () -> gateway(client, new StowResponseParser())
                .storeStudy("1.2.3", oneValidatedFile(), "token"));

        assertEquals("OUTCOME_UNKNOWN", ex.reason().name());
    }

    @Test
    void interruption_after_body_subscription_is_outcome_unknown_and_restores_interrupt() throws Exception {
        HttpClient client = throwingClient(new InterruptedException("interrupted"), true);

        try {
            ArchiveUnavailableException ex = assertThrows(ArchiveUnavailableException.class,
                () -> gateway(client, new StowResponseParser())
                    .storeStudy("1.2.3", oneValidatedFile(), "token"));

            assertEquals("OUTCOME_UNKNOWN", ex.reason().name());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void real_http_timeout_after_body_subscription_is_outcome_unknown() throws Exception {
        CountDownLatch requestBodyReceived = new CountDownLatch(1);
        CountDownLatch releaseResponse = new CountDownLatch(1);
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies/1.2.3", exchange -> {
            try {
                exchange.getRequestBody().transferTo(OutputStream.nullOutputStream());
                requestBodyReceived.countDown();
                releaseResponse.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                exchange.close();
                return;
            }
            byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        Path file = createDicomFile("1.2.3", "1.2.3.1", "1.2.3.1.1", (byte) 1);
        List<ValidatedDicom> files = List.of(
            new ValidatedDicom(0, file, "f.dcm", Files.size(file), "1.2.3", "1.2.3.1", "1.2.3.1.1", UID.SecondaryCaptureImageStorage, "h")
        );

        HttpDicomArchiveGateway gateway = new HttpDicomArchiveGateway(
            baseUrl,
            Duration.ofMillis(50),
            new StowResponseParser(),
            HttpClient.newHttpClient()
        );

        try {
            ArchiveUnavailableException ex = assertThrows(
                ArchiveUnavailableException.class,
                () -> gateway.storeStudy("1.2.3", files, "token")
            );

            assertTrue(requestBodyReceived.await(1, TimeUnit.SECONDS));
            assertEquals("OUTCOME_UNKNOWN", ex.reason().name());
        } finally {
            releaseResponse.countDown();
        }
    }

    @Test
    void connection_failure_throws_archive_unavailable_with_connection_reason() throws Exception {
        // Stop the server to simulate an unavailable archive.
        server.stop(0);

        Path file = createDicomFile("1.2.3", "1.2.3.1", "1.2.3.1.1", (byte) 1);
        List<ValidatedDicom> files = List.of(
            new ValidatedDicom(0, file, "f.dcm", Files.size(file), "1.2.3", "1.2.3.1", "1.2.3.1.1", UID.SecondaryCaptureImageStorage, "h")
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
    void missing_local_file_is_sanitized_internal_failure_and_makes_no_http_call() {
        Path nonexistent = tempDir.resolve("missing.dcm");
        List<ValidatedDicom> files = List.of(
            new ValidatedDicom(0, nonexistent, "missing.dcm", 100, "1.2.3", "1.2.3.1", "1.2.3.1.1", UID.SecondaryCaptureImageStorage, "h")
        );

        HttpClient client = mock(HttpClient.class);
        HttpDicomArchiveGateway gateway = gateway(client, new StowResponseParser());

        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> gateway.storeStudy("1.2.3", files, "token")
        );

        assertNull(ex.getCause());
        assertFalse(ex.getMessage().contains(nonexistent.toString()));
        assertFalse(ex.getMessage().contains("missing.dcm"));
        verifyNoInteractions(client);
    }

    @Test
    void multipart_related_body_publisher_preserves_format_and_boundaries() throws Exception {
        Path file1 = createDicomFile("1.2.3", "1.2.3.1", "1.2.3.1.1", (byte) 10);
        byte[] fileBytes = Files.readAllBytes(file1);

        List<ValidatedDicom> files = List.of(
            new ValidatedDicom(0, file1, "f1.dcm", fileBytes.length, "1.2.3", "1.2.3.1", "1.2.3.1.1", UID.SecondaryCaptureImageStorage, "h")
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

    private List<ValidatedDicom> oneValidatedFile() throws IOException {
        Path file = createDicomFile("1.2.3", "1.2.3.1", "1.2.3.1.1", (byte) 1);
        return List.of(new ValidatedDicom(
            0, file, "f.dcm", Files.size(file), "1.2.3", "1.2.3.1", "1.2.3.1.1",
            UID.SecondaryCaptureImageStorage, "h"));
    }

    private HttpDicomArchiveGateway gateway(HttpClient client, StowResponseParser parser) {
        return new HttpDicomArchiveGateway(baseUrl, Duration.ofSeconds(2), parser, client);
    }

    private void respondToStore(int status, String contentType, String body) {
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies/1.2.3", exchange -> {
            exchange.getRequestBody().transferTo(OutputStream.nullOutputStream());
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            if (contentType != null) {
                exchange.getResponseHeaders().set("Content-Type", contentType);
            }
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
    }

    private static String validStowBody(String sopUid) {
        return "{\"00081199\":{\"vr\":\"SQ\",\"Value\":["
            + "{\"00081155\":{\"vr\":\"UI\",\"Value\":[\"" + sopUid + "\"]}}]}}";
    }

    @SuppressWarnings("unchecked")
    private static HttpClient throwingClient(Exception failure, boolean subscribeBody) throws Exception {
        HttpClient client = mock(HttpClient.class);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(invocation -> {
            HttpRequest request = invocation.getArgument(0);
            if (subscribeBody) {
                subscribeAndCancel(request.bodyPublisher().orElseThrow());
            }
            throw failure;
        });
        return client;
    }

    private static void subscribeAndCancel(HttpRequest.BodyPublisher publisher) {
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.cancel();
            }

            @Override
            public void onNext(ByteBuffer item) {}

            @Override
            public void onError(Throwable throwable) {}

            @Override
            public void onComplete() {}
        });
    }
}
