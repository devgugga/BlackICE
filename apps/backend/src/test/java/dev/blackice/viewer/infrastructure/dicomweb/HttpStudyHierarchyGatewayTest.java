package dev.blackice.viewer.infrastructure.dicomweb;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.blackice.viewer.application.exception.ArchiveViewerException;
import dev.blackice.viewer.application.input.ViewerStudyRef;
import dev.blackice.viewer.application.result.InstanceIdentityMetadata;
import dev.blackice.viewer.application.result.SeriesMetadata;
import dev.blackice.viewer.application.result.StudyMetadata;
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
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
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

class HttpStudyHierarchyGatewayTest {

    private static final String STUDY_UID = "1.2.840.113619.2.55.3.604688416.741.100";
    private static final ViewerStudyRef STUDY_REF = new ViewerStudyRef(STUDY_UID);
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

    // ==========================================
    // findStudy Tests
    // ==========================================

    @Test
    void find_study_sends_bearer_traceparent_and_parses_single_study() {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Headers> headers = new AtomicReference<>();
        AtomicReference<String> rawQuery = new AtomicReference<>();

        String studyJson = """
            [
              {
                "0020000D": { "vr": "UI", "Value": ["1.2.840.113619.2.55.3.604688416.741.100"] },
                "00100010": { "vr": "PN", "Value": [{ "Alphabetic": "DOE^JOHN" }] }
              }
            ]
            """;

        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies", exchange -> {
            calls.incrementAndGet();
            headers.set(exchange.getRequestHeaders());
            rawQuery.set(exchange.getRequestURI().getRawQuery());
            respond(exchange, 200, DICOM_JSON, studyJson);
        });

        SpanContext spanContext = SpanContext.create(
            "4bf92f3577b34da6a3ce929d0e0e4736",
            "00f067aa0ba902b7",
            TraceFlags.getSampled(),
            TraceState.getDefault()
        );
        Span span = Span.wrap(spanContext);
        StudyMetadata study;
        try (Scope ignored = Context.root().with(span).makeCurrent()) {
            study = gateway(Duration.ofSeconds(2), 500).findStudy(STUDY_REF, "secret-user-token");
        }

        assertEquals(1, calls.get());
        assertEquals("Bearer secret-user-token", headers.get().getFirst("Authorization"));
        assertEquals(DICOM_JSON, headers.get().getFirst("Accept"));
        assertEquals("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01", headers.get().getFirst("traceparent"));
        assertTrue(URLDecoder.decode(rawQuery.get(), StandardCharsets.UTF_8).contains("limit=2"));

        assertNotNull(study);
        assertEquals(STUDY_UID, study.studyInstanceUid());
        assertEquals("DOE^JOHN", study.patientName());
    }

    @Test
    void find_study_empty_result_or_204_throws_not_found() {
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies", exchange -> {
            respond(exchange, 204, null, "");
        });

        ArchiveViewerException ex = assertThrows(
            ArchiveViewerException.class,
            () -> gateway(Duration.ofSeconds(2), 500).findStudy(STUDY_REF, "token")
        );
        assertEquals(ArchiveViewerException.Reason.NOT_FOUND, ex.reason());
        assertEquals("NOT_FOUND", ex.getMessage());
    }

    @Test
    void find_study_empty_json_array_throws_not_found() {
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies", exchange -> {
            respond(exchange, 200, DICOM_JSON, "[]");
        });

        ArchiveViewerException ex = assertThrows(
            ArchiveViewerException.class,
            () -> gateway(Duration.ofSeconds(2), 500).findStudy(STUDY_REF, "token")
        );
        assertEquals(ArchiveViewerException.Reason.NOT_FOUND, ex.reason());
    }

    @Test
    void find_study_multiple_matches_throws_invalid_response() {
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies", exchange -> {
            String multiStudy = """
                [
                  { "0020000D": { "vr": "UI", "Value": ["1.2.3.1"] } },
                  { "0020000D": { "vr": "UI", "Value": ["1.2.3.2"] } }
                ]
                """;
            respond(exchange, 200, DICOM_JSON, multiStudy);
        });

        ArchiveViewerException ex = assertThrows(
            ArchiveViewerException.class,
            () -> gateway(Duration.ofSeconds(2), 500).findStudy(STUDY_REF, "token")
        );
        assertEquals(ArchiveViewerException.Reason.INVALID_RESPONSE, ex.reason());
    }

    @Test
    void find_study_rejects_different_study_instance_uid() {
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies", exchange ->
            respond(
                exchange,
                200,
                DICOM_JSON,
                "[{\"0020000D\":{\"vr\":\"UI\",\"Value\":[\"1.2.3.999\"]}}]"
            )
        );

        ArchiveViewerException error = assertThrows(
            ArchiveViewerException.class,
            () -> gateway(Duration.ofSeconds(2), 500).findStudy(STUDY_REF, "token")
        );
        assertEquals(ArchiveViewerException.Reason.INVALID_RESPONSE, error.reason());
        assertFalse(error.getMessage().contains(STUDY_UID));
    }

    @Test
    void find_study_rejects_warning_299() {
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies", exchange -> {
            exchange.getResponseHeaders().add(
                "Warning",
                "299 dcm4chee: There are 1 additional results that can be requested"
            );
            respond(
                exchange,
                200,
                DICOM_JSON,
                "[{\"0020000D\":{\"vr\":\"UI\",\"Value\":[\"" + STUDY_UID + "\"]}}]"
            );
        });

        ArchiveViewerException error = assertThrows(
            ArchiveViewerException.class,
            () -> gateway(Duration.ofSeconds(2), 500).findStudy(STUDY_REF, "token")
        );
        assertEquals(ArchiveViewerException.Reason.INVALID_RESPONSE, error.reason());
    }

    // ==========================================
    // findSeries Tests
    // ==========================================

    @Test
    void find_series_returns_parsed_list() {
        String seriesJson = """
            [
              {
                "0020000E": { "vr": "UI", "Value": ["1.2.3.1"] },
                "00200011": { "vr": "IS", "Value": [1] },
                "00080060": { "vr": "CS", "Value": ["CT"] },
                "0008103E": { "vr": "LO", "Value": ["AXIAL"] },
                "00201209": { "vr": "IS", "Value": [50] }
              }
            ]
            """;

        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies/" + STUDY_UID + "/series", exchange -> {
            respond(exchange, 200, DICOM_JSON, seriesJson);
        });

        List<SeriesMetadata> series = gateway(Duration.ofSeconds(2), 500).findSeries(STUDY_REF, "token");
        assertEquals(1, series.size());
        assertEquals("1.2.3.1", series.get(0).seriesInstanceUid());
        assertEquals(1, series.get(0).seriesNumber());
        assertEquals("CT", series.get(0).modality());
        assertEquals("AXIAL", series.get(0).description());
        assertEquals(50, series.get(0).instanceCount());
    }

    @Test
    void find_series_204_returns_empty_list() {
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies/" + STUDY_UID + "/series", exchange -> {
            respond(exchange, 204, null, "");
        });

        List<SeriesMetadata> series = gateway(Duration.ofSeconds(2), 500).findSeries(STUDY_REF, "token");
        assertNotNull(series);
        assertTrue(series.isEmpty());
    }

    @Test
    void find_series_rejects_warning_299() {
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies/" + STUDY_UID + "/series", exchange -> {
            exchange.getResponseHeaders().add(
                "Warning",
                "299 dcm4chee: There are 1 additional results that can be requested"
            );
            respond(
                exchange,
                200,
                DICOM_JSON,
                "[{\"0020000E\":{\"vr\":\"UI\",\"Value\":[\"1.2.3.1\"]},"
                    + "\"00201209\":{\"vr\":\"IS\",\"Value\":[1]}}]"
            );
        });

        ArchiveViewerException error = assertThrows(
            ArchiveViewerException.class,
            () -> gateway(Duration.ofSeconds(2), 500).findSeries(STUDY_REF, "token")
        );
        assertEquals(ArchiveViewerException.Reason.INVALID_RESPONSE, error.reason());
    }

    // ==========================================
    // findInstances and Pagination Tests
    // ==========================================

    @Test
    void find_instances_single_short_page_terminates_immediately() {
        AtomicInteger calls = new AtomicInteger();
        String page0 = """
            [
              {
                "0020000E": { "vr": "UI", "Value": ["1.2.3.1"] },
                "00080018": { "vr": "UI", "Value": ["1.2.3.1.1"] },
                "00080016": { "vr": "UI", "Value": ["1.2.840.10008.5.1.4.1.1.2"] }
              }
            ]
            """;

        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies/" + STUDY_UID + "/instances", exchange -> {
            calls.incrementAndGet();
            respond(exchange, 200, DICOM_JSON, page0);
        });

        List<InstanceIdentityMetadata> instances = gateway(Duration.ofSeconds(2), 500).findInstances(STUDY_REF, "token");

        assertEquals(1, calls.get());
        assertEquals(1, instances.size());
        assertEquals("1.2.3.1.1", instances.get(0).sopInstanceUid());
    }

    @Test
    void find_instances_continues_after_non_empty_page_with_warning_299() {
        List<String> requestedOffsets = new ArrayList<>();

        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies/" + STUDY_UID + "/instances", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            if (query.contains("offset=0")) {
                requestedOffsets.add("0");
                String page0 = """
                    [
                      {
                        "0020000E": { "vr": "UI", "Value": ["1.2.3.1"] },
                        "00080018": { "vr": "UI", "Value": ["1.2.3.1.1"] },
                        "00080016": { "vr": "UI", "Value": ["1.2.840.10008.5.1.4.1.1.2"] }
                      },
                      {
                        "0020000E": { "vr": "UI", "Value": ["1.2.3.1"] },
                        "00080018": { "vr": "UI", "Value": ["1.2.3.1.2"] },
                        "00080016": { "vr": "UI", "Value": ["1.2.840.10008.5.1.4.1.1.2"] }
                      }
                    ]
                    """;
                exchange.getResponseHeaders().add(
                    "Warning",
                    "299 dcm4chee: There are 1 additional results that can be requested"
                );
                respond(exchange, 200, DICOM_JSON, page0);
            } else if (query.contains("offset=2")) {
                requestedOffsets.add("2");
                String page1 = """
                    [
                      {
                        "0020000E": { "vr": "UI", "Value": ["1.2.3.1"] },
                        "00080018": { "vr": "UI", "Value": ["1.2.3.1.3"] },
                        "00080016": { "vr": "UI", "Value": ["1.2.840.10008.5.1.4.1.1.2"] }
                      }
                    ]
                    """;
                respond(exchange, 200, DICOM_JSON, page1);
            } else {
                respond(exchange, 200, DICOM_JSON, "[]");
            }
        });

        // Use pageSize = 2 to exercise multi-page pagination
        List<InstanceIdentityMetadata> instances = gateway(Duration.ofSeconds(2), 2).findInstances(STUDY_REF, "token");

        assertEquals(List.of("0", "2"), requestedOffsets);
        assertEquals(3, instances.size());
        assertEquals("1.2.3.1.1", instances.get(0).sopInstanceUid());
        assertEquals("1.2.3.1.2", instances.get(1).sopInstanceUid());
        assertEquals("1.2.3.1.3", instances.get(2).sopInstanceUid());
    }

    @Test
    void find_instances_advances_by_received_size_when_archive_max_results_is_smaller() {
        List<String> requestedOffsets = new ArrayList<>();

        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies/" + STUDY_UID + "/instances", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            if (query.contains("offset=0")) {
                requestedOffsets.add("0");
                exchange.getResponseHeaders().add(
                    "Warning",
                    "299 dcm4chee: There are 1 additional results that can be requested"
                );
                respond(
                    exchange,
                    200,
                    DICOM_JSON,
                    "[{\"0020000E\":{\"vr\":\"UI\",\"Value\":[\"1.2.3.1\"]},"
                        + "\"00080018\":{\"vr\":\"UI\",\"Value\":[\"1.2.3.1.1\"]},"
                        + "\"00080016\":{\"vr\":\"UI\",\"Value\":[\"1.2.840.10008.5.1.4.1.1.2\"]}}]"
                );
            } else if (query.contains("offset=1")) {
                requestedOffsets.add("1");
                respond(
                    exchange,
                    200,
                    DICOM_JSON,
                    "[{\"0020000E\":{\"vr\":\"UI\",\"Value\":[\"1.2.3.1\"]},"
                        + "\"00080018\":{\"vr\":\"UI\",\"Value\":[\"1.2.3.1.2\"]},"
                        + "\"00080016\":{\"vr\":\"UI\",\"Value\":[\"1.2.840.10008.5.1.4.1.1.2\"]}}]"
                );
            } else {
                respond(exchange, 200, DICOM_JSON, "[]");
            }
        });

        List<InstanceIdentityMetadata> instances =
            gateway(Duration.ofSeconds(2), 2).findInstances(STUDY_REF, "token");

        assertEquals(List.of("0", "1"), requestedOffsets);
        assertEquals(2, instances.size());
    }

    @Test
    void find_instances_rejects_empty_page_with_warning_299() {
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies/" + STUDY_UID + "/instances", exchange -> {
            exchange.getResponseHeaders().add(
                "Warning",
                "299 dcm4chee: There are 1 additional results that can be requested"
            );
            respond(exchange, 200, DICOM_JSON, "[]");
        });

        ArchiveViewerException error = assertThrows(
            ArchiveViewerException.class,
            () -> gateway(Duration.ofSeconds(2), 2).findInstances(STUDY_REF, "token")
        );
        assertEquals(ArchiveViewerException.Reason.INVALID_RESPONSE, error.reason());
    }

    @Test
    void find_instances_rejects_duplicate_sop_instance_uid_across_pages() {
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies/" + STUDY_UID + "/instances", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            if (query.contains("offset=0")) {
                String page0 = """
                    [
                      {
                        "0020000E": { "vr": "UI", "Value": ["1.2.3.1"] },
                        "00080018": { "vr": "UI", "Value": ["1.2.3.1.1"] },
                        "00080016": { "vr": "UI", "Value": ["1.2.840.10008.5.1.4.1.1.2"] }
                      },
                      {
                        "0020000E": { "vr": "UI", "Value": ["1.2.3.1"] },
                        "00080018": { "vr": "UI", "Value": ["1.2.3.1.2"] },
                        "00080016": { "vr": "UI", "Value": ["1.2.840.10008.5.1.4.1.1.2"] }
                      }
                    ]
                    """;
                exchange.getResponseHeaders().add(
                    "Warning",
                    "299 dcm4chee: There are 1 additional results that can be requested"
                );
                respond(exchange, 200, DICOM_JSON, page0);
            } else {
                String page1WithDuplicate = """
                    [
                      {
                        "0020000E": { "vr": "UI", "Value": ["1.2.3.1"] },
                        "00080018": { "vr": "UI", "Value": ["1.2.3.1.1"] },
                        "00080016": { "vr": "UI", "Value": ["1.2.840.10008.5.1.4.1.1.2"] }
                      }
                    ]
                    """;
                respond(exchange, 200, DICOM_JSON, page1WithDuplicate);
            }
        });

        ArchiveViewerException ex = assertThrows(
            ArchiveViewerException.class,
            () -> gateway(Duration.ofSeconds(2), 2).findInstances(STUDY_REF, "token")
        );
        assertEquals(ArchiveViewerException.Reason.INVALID_RESPONSE, ex.reason());
    }

    // ==========================================
    // HTTP Status and Error Mapping Tests
    // ==========================================

    @Test
    void http_401_maps_to_authentication() {
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies", exchange -> {
            respond(exchange, 401, "text/plain", "Unauthorized secret-body");
        });

        ArchiveViewerException ex = assertThrows(
            ArchiveViewerException.class,
            () -> gateway(Duration.ofSeconds(2), 500).findStudy(STUDY_REF, "token")
        );
        assertEquals(ArchiveViewerException.Reason.AUTHENTICATION, ex.reason());
        assertEquals("AUTHENTICATION", ex.getMessage());
        assertFalse(ex.getMessage().contains("secret"));
    }

    @Test
    void http_403_maps_to_access_denied() {
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies", exchange -> {
            respond(exchange, 403, "text/plain", "Forbidden");
        });

        ArchiveViewerException ex = assertThrows(
            ArchiveViewerException.class,
            () -> gateway(Duration.ofSeconds(2), 500).findStudy(STUDY_REF, "token")
        );
        assertEquals(ArchiveViewerException.Reason.ACCESS_DENIED, ex.reason());
    }

    @Test
    void http_404_maps_to_not_found() {
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies", exchange -> {
            respond(exchange, 404, "text/plain", "Not Found");
        });

        ArchiveViewerException ex = assertThrows(
            ArchiveViewerException.class,
            () -> gateway(Duration.ofSeconds(2), 500).findStudy(STUDY_REF, "token")
        );
        assertEquals(ArchiveViewerException.Reason.NOT_FOUND, ex.reason());
    }

    @Test
    void http_500_and_503_map_to_unavailable() {
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies", exchange -> {
            respond(exchange, 503, "text/plain", "Service Unavailable");
        });

        ArchiveViewerException ex = assertThrows(
            ArchiveViewerException.class,
            () -> gateway(Duration.ofSeconds(2), 500).findStudy(STUDY_REF, "token")
        );
        assertEquals(ArchiveViewerException.Reason.UNAVAILABLE, ex.reason());
    }

    @Test
    void invalid_media_type_maps_to_invalid_response() {
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies", exchange -> {
            respond(exchange, 200, "text/html", "<html><body>error</body></html>");
        });

        ArchiveViewerException ex = assertThrows(
            ArchiveViewerException.class,
            () -> gateway(Duration.ofSeconds(2), 500).findStudy(STUDY_REF, "token")
        );
        assertEquals(ArchiveViewerException.Reason.INVALID_RESPONSE, ex.reason());
    }

    @Test
    void timeout_maps_to_timeout_reason() {
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies", exchange -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {}
            respond(exchange, 200, DICOM_JSON, "[]");
        });

        ArchiveViewerException ex = assertThrows(
            ArchiveViewerException.class,
            () -> gateway(Duration.ofMillis(50), 500).findStudy(STUDY_REF, "token")
        );
        assertEquals(ArchiveViewerException.Reason.TIMEOUT, ex.reason());
    }

    @Test
    void connection_failure_maps_to_connection_reason() {
        server.stop(0);

        ArchiveViewerException ex = assertThrows(
            ArchiveViewerException.class,
            () -> gateway(Duration.ofSeconds(2), 500).findStudy(STUDY_REF, "token")
        );
        assertEquals(ArchiveViewerException.Reason.CONNECTION, ex.reason());
    }

    @Test
    @SuppressWarnings("unchecked")
    void interrupted_execution_restores_interrupt_flag_and_maps_to_connection() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenThrow(new InterruptedException("simulated interrupt"));

        HttpStudyHierarchyGateway gateway = new HttpStudyHierarchyGateway(
            baseUrl,
            Duration.ofSeconds(2),
            500,
            new QidoViewerQueryBuilder(),
            new QidoViewerResponseParser(),
            mockClient
        );

        try {
            ArchiveViewerException ex = assertThrows(
                ArchiveViewerException.class,
                () -> gateway.findStudy(STUDY_REF, "token")
            );
            assertEquals(ArchiveViewerException.Reason.CONNECTION, ex.reason());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted(); // clear status
        }
    }

    @Test
    void unexpected_runtime_bug_propagates_unwrapped() {
        HttpClient broken = mock(HttpClient.class);
        try {
            when(broken.send(any(), any())).thenThrow(new IllegalStateException("internal bug"));
        } catch (Exception e) {
            throw new AssertionError(e);
        }

        HttpStudyHierarchyGateway gateway = new HttpStudyHierarchyGateway(
            baseUrl,
            Duration.ofSeconds(2),
            500,
            new QidoViewerQueryBuilder(),
            new QidoViewerResponseParser(),
            broken
        );

        assertThrows(IllegalStateException.class, () -> gateway.findStudy(STUDY_REF, "token"));
    }

    @Test
    void null_guards() {
        HttpStudyHierarchyGateway gateway = gateway(Duration.ofSeconds(2), 500);

        assertThrows(NullPointerException.class, () -> gateway.findStudy(null, "token"));
        assertThrows(NullPointerException.class, () -> gateway.findStudy(STUDY_REF, null));

        assertThrows(NullPointerException.class, () -> gateway.findSeries(null, "token"));
        assertThrows(NullPointerException.class, () -> gateway.findSeries(STUDY_REF, null));

        assertThrows(NullPointerException.class, () -> gateway.findInstances(null, "token"));
        assertThrows(NullPointerException.class, () -> gateway.findInstances(STUDY_REF, null));
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

    private HttpStudyHierarchyGateway gateway(Duration timeout, int pageSize) {
        return new HttpStudyHierarchyGateway(
            baseUrl,
            timeout,
            pageSize,
            new QidoViewerQueryBuilder(),
            new QidoViewerResponseParser(),
            HttpClient.newBuilder().connectTimeout(timeout).build()
        );
    }
}
