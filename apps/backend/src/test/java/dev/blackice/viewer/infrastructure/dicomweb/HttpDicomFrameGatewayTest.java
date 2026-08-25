package dev.blackice.viewer.infrastructure.dicomweb;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.blackice.viewer.application.exception.ArchiveViewerException;
import dev.blackice.viewer.application.input.ViewerInstanceRef;
import dev.blackice.viewer.application.result.FrameStream;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HttpDicomFrameGatewayTest {

    private static final String STUDY_UID = "1.2.840.113619.2.55.3.604688435.123.1599720123.467";
    private static final String SERIES_UID = "1.2.840.113619.2.55.3.604688435.124";
    private static final String SOP_UID = "1.2.840.113619.2.55.3.604688435.126";
    private static final ViewerInstanceRef INSTANCE_REF = new ViewerInstanceRef(STUDY_UID, SERIES_UID, SOP_UID);

    private static final String EXPECTED_ACCEPT = "multipart/related; type=\"application/octet-stream\"; transfer-syntax=*";
    private static final String SAMPLE_MULTIPART_CONTENT_TYPE =
        "multipart/related; type=\"application/octet-stream\"; boundary=--frame-boundary-123";

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
    void retrieve_sends_exact_wado_frame_request_and_returns_stream() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Headers> headers = new AtomicReference<>();
        AtomicReference<String> requestPath = new AtomicReference<>();
        AtomicReference<String> requestMethod = new AtomicReference<>();

        byte[] fakeMultipartPayload = ("--frame-boundary-123\r\n"
            + "Content-Type: application/octet-stream; transfer-syntax=1.2.840.10008.1.2.1\r\n\r\n"
            + "RAW_PIXEL_BYTES_FOR_FRAME_1\r\n"
            + "--frame-boundary-123--\r\n").getBytes(StandardCharsets.UTF_8);

        String expectedPath = "/dcm4chee-arc/aets/DCM4CHEE/rs/studies/"
            + STUDY_UID + "/series/" + SERIES_UID + "/instances/" + SOP_UID + "/frames/1";

        server.createContext(expectedPath, exchange -> {
            calls.incrementAndGet();
            headers.set(exchange.getRequestHeaders());
            requestPath.set(exchange.getRequestURI().getPath());
            requestMethod.set(exchange.getRequestMethod());
            respond(exchange, 200, SAMPLE_MULTIPART_CONTENT_TYPE, fakeMultipartPayload);
        });

        SpanContext spanContext = SpanContext.create(
            "4bf92f3577b34da6a3ce929d0e0e4736",
            "00f067aa0ba902b7",
            TraceFlags.getSampled(),
            TraceState.getDefault()
        );
        Span span = Span.wrap(spanContext);

        FrameStream stream;
        try (Scope ignored = Context.root().with(span).makeCurrent()) {
            stream = gateway(Duration.ofSeconds(2)).retrieveFirstFrame(INSTANCE_REF, "secret-user-token");
        }

        assertEquals(1, calls.get());
        assertEquals("GET", requestMethod.get());
        assertEquals(expectedPath, requestPath.get());
        assertEquals("Bearer secret-user-token", headers.get().getFirst("Authorization"));
        assertEquals(EXPECTED_ACCEPT, headers.get().getFirst("Accept"));
        assertEquals("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01", headers.get().getFirst("traceparent"));

        assertNotNull(stream);
        assertEquals(SAMPLE_MULTIPART_CONTENT_TYPE, stream.contentType());
        try (stream) {
            byte[] actualBytes = stream.body().readAllBytes();
            assertArrayEquals(fakeMultipartPayload, actualBytes);
        }
    }

    @Test
    void retrieve_accepts_boundary_with_quotes_and_custom_transfer_syntax() throws IOException {
        String contentType = "multipart/related; boundary=\"sample-boundary\"; type=application/octet-stream; transfer-syntax=1.2.840.10008.1.2.4.70";
        byte[] payload = "frame-bytes".getBytes(StandardCharsets.UTF_8);

        server.createContext(framePath(), exchange -> {
            respond(exchange, 200, contentType, payload);
        });

        try (FrameStream stream = gateway(Duration.ofSeconds(2)).retrieveFirstFrame(INSTANCE_REF, "token")) {
            assertNotNull(stream);
            assertEquals(contentType, stream.contentType());
            assertArrayEquals(payload, stream.body().readAllBytes());
        }
    }

    @Test
    void retrieve_missing_content_type_maps_to_invalid_response() {
        server.createContext(framePath(), exchange -> {
            respond(exchange, 200, null, "bytes".getBytes(StandardCharsets.UTF_8));
        });

        ArchiveViewerException ex = assertThrows(
            ArchiveViewerException.class,
            () -> gateway(Duration.ofSeconds(2)).retrieveFirstFrame(INSTANCE_REF, "token")
        );
        assertEquals(ArchiveViewerException.Reason.INVALID_RESPONSE, ex.reason());
    }

    @Test
    void retrieve_wrong_media_type_json_maps_to_invalid_response() {
        server.createContext(framePath(), exchange -> {
            respond(exchange, 200, "application/json", "{}".getBytes(StandardCharsets.UTF_8));
        });

        ArchiveViewerException ex = assertThrows(
            ArchiveViewerException.class,
            () -> gateway(Duration.ofSeconds(2)).retrieveFirstFrame(INSTANCE_REF, "token")
        );
        assertEquals(ArchiveViewerException.Reason.INVALID_RESPONSE, ex.reason());
    }

    @Test
    void retrieve_multipart_without_octet_stream_type_maps_to_invalid_response() {
        server.createContext(framePath(), exchange -> {
            respond(exchange, 200, "multipart/related; type=\"image/jpeg\"; boundary=xyz", "bytes".getBytes(StandardCharsets.UTF_8));
        });

        ArchiveViewerException ex = assertThrows(
            ArchiveViewerException.class,
            () -> gateway(Duration.ofSeconds(2)).retrieveFirstFrame(INSTANCE_REF, "token")
        );
        assertEquals(ArchiveViewerException.Reason.INVALID_RESPONSE, ex.reason());
    }

    @Test
    void retrieve_multipart_without_boundary_maps_to_invalid_response() {
        server.createContext(framePath(), exchange -> {
            respond(exchange, 200, "multipart/related; type=\"application/octet-stream\"", "bytes".getBytes(StandardCharsets.UTF_8));
        });

        ArchiveViewerException ex = assertThrows(
            ArchiveViewerException.class,
            () -> gateway(Duration.ofSeconds(2)).retrieveFirstFrame(INSTANCE_REF, "token")
        );
        assertEquals(ArchiveViewerException.Reason.INVALID_RESPONSE, ex.reason());
    }

    @Test
    void retrieve_multipart_with_empty_boundary_maps_to_invalid_response() {
        server.createContext(framePath(), exchange -> {
            respond(exchange, 200, "multipart/related; type=\"application/octet-stream\"; boundary=\"\"", "bytes".getBytes(StandardCharsets.UTF_8));
        });

        ArchiveViewerException ex = assertThrows(
            ArchiveViewerException.class,
            () -> gateway(Duration.ofSeconds(2)).retrieveFirstFrame(INSTANCE_REF, "token")
        );
        assertEquals(ArchiveViewerException.Reason.INVALID_RESPONSE, ex.reason());
    }

    @Test
    void retrieve_401_maps_to_authentication() {
        server.createContext(framePath(), exchange -> {
            respond(exchange, 401, "text/plain", "Unauthorized".getBytes(StandardCharsets.UTF_8));
        });

        ArchiveViewerException ex = assertThrows(
            ArchiveViewerException.class,
            () -> gateway(Duration.ofSeconds(2)).retrieveFirstFrame(INSTANCE_REF, "token")
        );
        assertEquals(ArchiveViewerException.Reason.AUTHENTICATION, ex.reason());
        assertEquals("AUTHENTICATION", ex.getMessage());
        assertFalse(ex.getMessage().contains("secret"));
    }

    @Test
    void retrieve_403_maps_to_access_denied() {
        server.createContext(framePath(), exchange -> {
            respond(exchange, 403, "text/plain", "Forbidden".getBytes(StandardCharsets.UTF_8));
        });

        ArchiveViewerException ex = assertThrows(
            ArchiveViewerException.class,
            () -> gateway(Duration.ofSeconds(2)).retrieveFirstFrame(INSTANCE_REF, "token")
        );
        assertEquals(ArchiveViewerException.Reason.ACCESS_DENIED, ex.reason());
    }

    @Test
    void retrieve_404_maps_to_not_found() {
        server.createContext(framePath(), exchange -> {
            respond(exchange, 404, "text/plain", "Not Found".getBytes(StandardCharsets.UTF_8));
        });

        ArchiveViewerException ex = assertThrows(
            ArchiveViewerException.class,
            () -> gateway(Duration.ofSeconds(2)).retrieveFirstFrame(INSTANCE_REF, "token")
        );
        assertEquals(ArchiveViewerException.Reason.NOT_FOUND, ex.reason());
    }

    @Test
    void retrieve_500_and_503_map_to_unavailable() {
        server.createContext(framePath(), exchange -> {
            respond(exchange, 503, "text/plain", "Service Unavailable".getBytes(StandardCharsets.UTF_8));
        });

        ArchiveViewerException ex = assertThrows(
            ArchiveViewerException.class,
            () -> gateway(Duration.ofSeconds(2)).retrieveFirstFrame(INSTANCE_REF, "token")
        );
        assertEquals(ArchiveViewerException.Reason.UNAVAILABLE, ex.reason());
    }

    @Test
    void retrieve_timeout_maps_to_timeout_reason() {
        server.createContext(framePath(), exchange -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {}
            respond(exchange, 200, SAMPLE_MULTIPART_CONTENT_TYPE, "data".getBytes(StandardCharsets.UTF_8));
        });

        ArchiveViewerException ex = assertThrows(
            ArchiveViewerException.class,
            () -> gateway(Duration.ofMillis(50)).retrieveFirstFrame(INSTANCE_REF, "token")
        );
        assertEquals(ArchiveViewerException.Reason.TIMEOUT, ex.reason());
    }

    @Test
    void retrieve_connection_failure_maps_to_connection_reason() {
        server.stop(0);

        ArchiveViewerException ex = assertThrows(
            ArchiveViewerException.class,
            () -> gateway(Duration.ofSeconds(2)).retrieveFirstFrame(INSTANCE_REF, "token")
        );
        assertEquals(ArchiveViewerException.Reason.CONNECTION, ex.reason());
    }

    @Test
    @SuppressWarnings("unchecked")
    void retrieve_interrupted_execution_restores_interrupt_flag_and_maps_to_connection() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenThrow(new InterruptedException("simulated interrupt"));

        HttpDicomFrameGateway gw = new HttpDicomFrameGateway(
            baseUrl,
            Duration.ofSeconds(2),
            mockClient
        );

        try {
            ArchiveViewerException ex = assertThrows(
                ArchiveViewerException.class,
                () -> gw.retrieveFirstFrame(INSTANCE_REF, "token")
            );
            assertEquals(ArchiveViewerException.Reason.CONNECTION, ex.reason());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void retrieve_unexpected_runtime_bug_propagates_unwrapped() {
        HttpClient broken = mock(HttpClient.class);
        try {
            when(broken.send(any(), any())).thenThrow(new IllegalStateException("internal bug"));
        } catch (Exception e) {
            throw new AssertionError(e);
        }

        HttpDicomFrameGateway gw = new HttpDicomFrameGateway(
            baseUrl,
            Duration.ofSeconds(2),
            broken
        );

        assertThrows(IllegalStateException.class, () -> gw.retrieveFirstFrame(INSTANCE_REF, "token"));
    }

    @Test
    void retrieve_null_guards() {
        HttpDicomFrameGateway gw = gateway(Duration.ofSeconds(2));
        assertThrows(NullPointerException.class, () -> gw.retrieveFirstFrame(null, "token"));
        assertThrows(NullPointerException.class, () -> gw.retrieveFirstFrame(INSTANCE_REF, null));
    }

    private String framePath() {
        return "/dcm4chee-arc/aets/DCM4CHEE/rs/studies/"
            + STUDY_UID + "/series/" + SERIES_UID + "/instances/" + SOP_UID + "/frames/1";
    }

    private void respond(HttpExchange exchange, int statusCode, String contentType, byte[] body) throws IOException {
        if (contentType != null) {
            exchange.getResponseHeaders().set("Content-Type", contentType);
        }
        exchange.sendResponseHeaders(statusCode, (body != null && body.length > 0) ? body.length : -1);
        if (body != null && body.length > 0) {
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        }
        exchange.close();
    }

    private HttpDicomFrameGateway gateway(Duration timeout) {
        return new HttpDicomFrameGateway(
            baseUrl,
            timeout,
            HttpClient.newBuilder().connectTimeout(timeout).build()
        );
    }
}
