package dev.blackice.viewer.infrastructure.dicomweb;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.blackice.viewer.application.exception.ArchiveViewerException;
import dev.blackice.viewer.application.input.ViewerSeriesRef;
import dev.blackice.viewer.application.result.ViewerInstance;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HttpSeriesMetadataGatewayTest {

    private static final String STUDY_UID = "1.2.840.113619.2.55.3.604688435.123.1599720123.467";
    private static final String SERIES_UID = "1.2.840.113619.2.55.3.604688435.124";
    private static final String SOP_UID = "1.2.840.113619.2.55.3.604688435.126";
    private static final String CT_SOP_CLASS = "1.2.840.10008.5.1.4.1.1.2";
    private static final ViewerSeriesRef SERIES_REF = new ViewerSeriesRef(STUDY_UID, SERIES_UID);
    private static final String DICOM_JSON = "application/dicom+json";

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
    void retrieve_sends_exact_wado_metadata_request_and_parses_response() {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Headers> headers = new AtomicReference<>();
        AtomicReference<String> requestPath = new AtomicReference<>();

        String responseBody = """
            [
              {
                "0020000D": { "vr": "UI", "Value": ["%s"] },
                "0020000E": { "vr": "UI", "Value": ["%s"] },
                "00080018": { "vr": "UI", "Value": ["%s"] },
                "00080016": { "vr": "UI", "Value": ["%s"] },
                "00200013": { "vr": "IS", "Value": [1] },
                "00280010": { "vr": "US", "Value": [512] },
                "00280011": { "vr": "US", "Value": [512] },
                "00280002": { "vr": "US", "Value": [1] },
                "00280004": { "vr": "CS", "Value": ["MONOCHROME2"] },
                "00280100": { "vr": "US", "Value": [16] },
                "00280101": { "vr": "US", "Value": [12] },
                "00280102": { "vr": "US", "Value": [11] },
                "00280103": { "vr": "US", "Value": [1] },
                "00280030": { "vr": "DS", "Value": [0.5, 0.5] },
                "00281052": { "vr": "DS", "Value": [-1024] },
                "00281053": { "vr": "DS", "Value": [1] }
              }
            ]
            """.formatted(STUDY_UID, SERIES_UID, SOP_UID, CT_SOP_CLASS);

        String expectedPath = "/dcm4chee-arc/aets/DCM4CHEE/rs/studies/" + STUDY_UID + "/series/" + SERIES_UID + "/metadata";

        server.createContext(expectedPath, exchange -> {
            calls.incrementAndGet();
            headers.set(exchange.getRequestHeaders());
            requestPath.set(exchange.getRequestURI().getPath());
            respond(exchange, 200, DICOM_JSON, responseBody);
        });

        SpanContext spanContext = SpanContext.create(
            "4bf92f3577b34da6a3ce929d0e0e4736",
            "00f067aa0ba902b7",
            TraceFlags.getSampled(),
            TraceState.getDefault()
        );
        Span span = Span.wrap(spanContext);

        List<ViewerInstance> instances;
        try (Scope ignored = Context.root().with(span).makeCurrent()) {
            instances = gateway(Duration.ofSeconds(2)).retrieve(SERIES_REF, "secret-user-token");
        }

        assertEquals(1, calls.get());
        assertEquals(expectedPath, requestPath.get());
        assertEquals("Bearer secret-user-token", headers.get().getFirst("Authorization"));
        assertEquals(DICOM_JSON, headers.get().getFirst("Accept"));
        assertEquals("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01", headers.get().getFirst("traceparent"));

        assertNotNull(instances);
        assertEquals(1, instances.size());
        assertEquals(SOP_UID, instances.getFirst().sopInstanceUid());
    }

    @Test
    void retrieve_204_returns_empty_list() {
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies/" + STUDY_UID + "/series/" + SERIES_UID + "/metadata", exchange -> {
            respond(exchange, 204, null, "");
        });

        List<ViewerInstance> instances = gateway(Duration.ofSeconds(2)).retrieve(SERIES_REF, "token");
        assertNotNull(instances);
        assertTrue(instances.isEmpty());
    }

    @Test
    void retrieve_401_maps_to_authentication() {
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies/" + STUDY_UID + "/series/" + SERIES_UID + "/metadata", exchange -> {
            respond(exchange, 401, "text/plain", "Unauthorized");
        });

        ArchiveViewerException ex = assertThrows(
            ArchiveViewerException.class,
            () -> gateway(Duration.ofSeconds(2)).retrieve(SERIES_REF, "token")
        );
        assertEquals(ArchiveViewerException.Reason.AUTHENTICATION, ex.reason());
        assertEquals("AUTHENTICATION", ex.getMessage());
        assertFalse(ex.getMessage().contains("secret"));
    }

    @Test
    void retrieve_403_maps_to_access_denied() {
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies/" + STUDY_UID + "/series/" + SERIES_UID + "/metadata", exchange -> {
            respond(exchange, 403, "text/plain", "Forbidden");
        });

        ArchiveViewerException ex = assertThrows(
            ArchiveViewerException.class,
            () -> gateway(Duration.ofSeconds(2)).retrieve(SERIES_REF, "token")
        );
        assertEquals(ArchiveViewerException.Reason.ACCESS_DENIED, ex.reason());
    }

    @Test
    void retrieve_404_maps_to_not_found() {
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies/" + STUDY_UID + "/series/" + SERIES_UID + "/metadata", exchange -> {
            respond(exchange, 404, "text/plain", "Not Found");
        });

        ArchiveViewerException ex = assertThrows(
            ArchiveViewerException.class,
            () -> gateway(Duration.ofSeconds(2)).retrieve(SERIES_REF, "token")
        );
        assertEquals(ArchiveViewerException.Reason.NOT_FOUND, ex.reason());
    }

    @Test
    void retrieve_500_and_503_map_to_unavailable() {
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies/" + STUDY_UID + "/series/" + SERIES_UID + "/metadata", exchange -> {
            respond(exchange, 503, "text/plain", "Service Unavailable");
        });

        ArchiveViewerException ex = assertThrows(
            ArchiveViewerException.class,
            () -> gateway(Duration.ofSeconds(2)).retrieve(SERIES_REF, "token")
        );
        assertEquals(ArchiveViewerException.Reason.UNAVAILABLE, ex.reason());
    }

    @Test
    void retrieve_wrong_media_type_maps_to_invalid_response() {
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies/" + STUDY_UID + "/series/" + SERIES_UID + "/metadata", exchange -> {
            respond(exchange, 200, "application/json", "[]");
        });

        ArchiveViewerException ex = assertThrows(
            ArchiveViewerException.class,
            () -> gateway(Duration.ofSeconds(2)).retrieve(SERIES_REF, "token")
        );
        assertEquals(ArchiveViewerException.Reason.INVALID_RESPONSE, ex.reason());
    }

    @Test
    void retrieve_malformed_json_maps_to_invalid_response() {
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies/" + STUDY_UID + "/series/" + SERIES_UID + "/metadata", exchange -> {
            respond(exchange, 200, DICOM_JSON, "corrupted json");
        });

        ArchiveViewerException ex = assertThrows(
            ArchiveViewerException.class,
            () -> gateway(Duration.ofSeconds(2)).retrieve(SERIES_REF, "token")
        );
        assertEquals(ArchiveViewerException.Reason.INVALID_RESPONSE, ex.reason());
    }

    @Test
    void retrieve_timeout_maps_to_timeout_reason() {
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies/" + STUDY_UID + "/series/" + SERIES_UID + "/metadata", exchange -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {}
            respond(exchange, 200, DICOM_JSON, "[]");
        });

        ArchiveViewerException ex = assertThrows(
            ArchiveViewerException.class,
            () -> gateway(Duration.ofMillis(50)).retrieve(SERIES_REF, "token")
        );
        assertEquals(ArchiveViewerException.Reason.TIMEOUT, ex.reason());
    }

    @Test
    void retrieve_connection_failure_maps_to_connection_reason() {
        server.stop(0);

        ArchiveViewerException ex = assertThrows(
            ArchiveViewerException.class,
            () -> gateway(Duration.ofSeconds(2)).retrieve(SERIES_REF, "token")
        );
        assertEquals(ArchiveViewerException.Reason.CONNECTION, ex.reason());
    }

    @Test
    @SuppressWarnings("unchecked")
    void retrieve_interrupted_execution_restores_interrupt_flag_and_maps_to_connection() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenThrow(new InterruptedException("simulated interrupt"));

        HttpSeriesMetadataGateway gw = new HttpSeriesMetadataGateway(
            baseUrl,
            Duration.ofSeconds(2),
            new WadoSeriesMetadataParser(),
            mockClient
        );

        try {
            ArchiveViewerException ex = assertThrows(
                ArchiveViewerException.class,
                () -> gw.retrieve(SERIES_REF, "token")
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

        HttpSeriesMetadataGateway gw = new HttpSeriesMetadataGateway(
            baseUrl,
            Duration.ofSeconds(2),
            new WadoSeriesMetadataParser(),
            broken
        );

        assertThrows(IllegalStateException.class, () -> gw.retrieve(SERIES_REF, "token"));
    }

    @Test
    void retrieve_null_guards() {
        HttpSeriesMetadataGateway gw = gateway(Duration.ofSeconds(2));
        assertThrows(NullPointerException.class, () -> gw.retrieve(null, "token"));
        assertThrows(NullPointerException.class, () -> gw.retrieve(SERIES_REF, null));
    }

    private void respond(HttpExchange exchange, int statusCode, String contentType, String body) throws IOException {
        byte[] bytes = (body != null && !body.isEmpty()) ? body.getBytes(StandardCharsets.UTF_8) : new byte[0];
        if (contentType != null) {
            exchange.getResponseHeaders().set("Content-Type", contentType);
        }
        exchange.sendResponseHeaders(statusCode, bytes.length > 0 ? bytes.length : -1);
        if (bytes.length > 0) {
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
        exchange.close();
    }

    private HttpSeriesMetadataGateway gateway(Duration timeout) {
        return new HttpSeriesMetadataGateway(
            baseUrl,
            timeout,
            new WadoSeriesMetadataParser(),
            HttpClient.newBuilder().connectTimeout(timeout).build()
        );
    }
}
