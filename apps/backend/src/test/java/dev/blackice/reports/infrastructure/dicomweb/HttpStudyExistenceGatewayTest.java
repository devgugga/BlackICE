package dev.blackice.reports.infrastructure.dicomweb;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.blackice.reports.application.exception.ArchiveStudyLookupException;
import dev.blackice.reports.application.input.ReportStudyRef;
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

class HttpStudyExistenceGatewayTest {

    private static final String STUDY_UID = "1.2.840.113619.2.55.3.604688416.741.100";
    private static final ReportStudyRef STUDY_REF = new ReportStudyRef(STUDY_UID);
    private static final String DICOM_JSON = "application/dicom+json";

    private static final String MATCHING_STUDY_BODY = """
        [
          {
            "0020000D": {
              "vr": "UI",
              "Value": ["1.2.840.113619.2.55.3.604688416.741.100"]
            }
          }
        ]
        """;

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
    void exists_sends_one_authenticated_qido_request_with_w3c_traceparent_and_returns_true() {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Headers> headers = new AtomicReference<>();
        AtomicReference<String> rawQuery = new AtomicReference<>();

        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies", exchange -> {
            calls.incrementAndGet();
            headers.set(exchange.getRequestHeaders());
            rawQuery.set(exchange.getRequestURI().getRawQuery());
            respond(exchange, 200, DICOM_JSON, MATCHING_STUDY_BODY);
        });

        SpanContext spanContext = SpanContext.create(
            "4bf92f3577b34da6a3ce929d0e0e4736",
            "00f067aa0ba902b7",
            TraceFlags.getSampled(),
            TraceState.getDefault()
        );
        Span span = Span.wrap(spanContext);
        boolean exists;
        try (Scope ignored = Context.root().with(span).makeCurrent()) {
            exists = gateway(Duration.ofSeconds(2)).exists(STUDY_REF, "secret-user-token");
        }

        assertEquals(1, calls.get());
        assertEquals("Bearer secret-user-token", headers.get().getFirst("Authorization"));
        assertEquals(DICOM_JSON, headers.get().getFirst("Accept"));
        assertEquals("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01", headers.get().getFirst("traceparent"));
        assertNotNull(rawQuery.get());

        String decodedQuery = URLDecoder.decode(rawQuery.get(), StandardCharsets.UTF_8);
        assertTrue(decodedQuery.contains("StudyInstanceUID=" + STUDY_UID));
        assertTrue(decodedQuery.contains("limit=1"));
        assertTrue(decodedQuery.contains("includefield=0020000D"));
        assertTrue(exists);
    }

    @Test
    void exists_returns_false_for_empty_json_array() {
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies", exchange -> {
            respond(exchange, 200, DICOM_JSON, "[]");
        });

        boolean exists = gateway(Duration.ofSeconds(2)).exists(STUDY_REF, "token");
        assertFalse(exists);
    }

    @Test
    void exists_returns_false_for_http_204_no_content() {
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies", exchange -> {
            respond(exchange, 204, null, "");
        });

        boolean exists = gateway(Duration.ofSeconds(2)).exists(STUDY_REF, "token");
        assertFalse(exists);
    }

    @Test
    void exists_returns_false_for_http_404_not_found() {
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies", exchange -> {
            respond(exchange, 404, "text/plain", "Not Found");
        });

        boolean exists = gateway(Duration.ofSeconds(2)).exists(STUDY_REF, "token");
        assertFalse(exists);
    }

    @Test
    void http_401_and_403_throw_archive_auth_failed_without_leaking_token_or_body() {
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies", exchange -> {
            respond(exchange, 401, "text/plain", "Unauthorized secret-details");
        });

        ArchiveStudyLookupException ex401 = assertThrows(
            ArchiveStudyLookupException.class,
            () -> gateway(Duration.ofSeconds(2)).exists(STUDY_REF, "secret-user-token")
        );
        assertEquals(ArchiveStudyLookupException.Reason.ARCHIVE_AUTH_FAILED, ex401.reason());
        assertEquals("ARCHIVE_AUTH_FAILED", ex401.getMessage());
        assertFalse(ex401.getMessage().contains("secret"));

        server.removeContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies");
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies", exchange -> {
            respond(exchange, 403, "text/plain", "Forbidden secret-forbidden-details");
        });

        ArchiveStudyLookupException ex403 = assertThrows(
            ArchiveStudyLookupException.class,
            () -> gateway(Duration.ofSeconds(2)).exists(STUDY_REF, "secret-user-token")
        );
        assertEquals(ArchiveStudyLookupException.Reason.ARCHIVE_AUTH_FAILED, ex403.reason());
        assertEquals("ARCHIVE_AUTH_FAILED", ex403.getMessage());
        assertFalse(ex403.getMessage().contains("secret"));
    }

    @Test
    void http_500_and_503_throw_archive_unavailable() {
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies", exchange -> {
            respond(exchange, 503, "text/plain", "Service Unavailable");
        });

        ArchiveStudyLookupException ex = assertThrows(
            ArchiveStudyLookupException.class,
            () -> gateway(Duration.ofSeconds(2)).exists(STUDY_REF, "token")
        );
        assertEquals(ArchiveStudyLookupException.Reason.ARCHIVE_UNAVAILABLE, ex.reason());
        assertEquals("ARCHIVE_UNAVAILABLE", ex.getMessage());
    }

    @Test
    void timeout_throws_archive_unavailable() {
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies", exchange -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {
            }
            respond(exchange, 200, DICOM_JSON, MATCHING_STUDY_BODY);
        });

        ArchiveStudyLookupException ex = assertThrows(
            ArchiveStudyLookupException.class,
            () -> gateway(Duration.ofMillis(50)).exists(STUDY_REF, "token")
        );
        assertEquals(ArchiveStudyLookupException.Reason.ARCHIVE_UNAVAILABLE, ex.reason());
    }

    @Test
    void connection_failure_throws_archive_unavailable() {
        server.stop(0);

        ArchiveStudyLookupException ex = assertThrows(
            ArchiveStudyLookupException.class,
            () -> gateway(Duration.ofSeconds(2)).exists(STUDY_REF, "token")
        );
        assertEquals(ArchiveStudyLookupException.Reason.ARCHIVE_UNAVAILABLE, ex.reason());
    }

    @Test
    @SuppressWarnings("unchecked")
    void interrupted_execution_restores_interrupt_flag_and_throws_archive_unavailable() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenThrow(new InterruptedException("simulated interrupt"));

        HttpStudyExistenceGateway gateway = new HttpStudyExistenceGateway(
            baseUrl,
            Duration.ofSeconds(2),
            new ReportQidoQueryBuilder(),
            new ReportQidoResponseParser(),
            mockClient
        );

        try {
            ArchiveStudyLookupException ex = assertThrows(
                ArchiveStudyLookupException.class,
                () -> gateway.exists(STUDY_REF, "token")
            );
            assertEquals(ArchiveStudyLookupException.Reason.ARCHIVE_UNAVAILABLE, ex.reason());
            assertTrue(Thread.currentThread().isInterrupted(), "Interrupt flag must be restored");
        } finally {
            Thread.interrupted(); // clear status
        }
    }

    @Test
    void wrong_content_type_throws_invalid_response() {
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies", exchange -> {
            respond(exchange, 200, "text/html", MATCHING_STUDY_BODY);
        });

        ArchiveStudyLookupException ex = assertThrows(
            ArchiveStudyLookupException.class,
            () -> gateway(Duration.ofSeconds(2)).exists(STUDY_REF, "token")
        );
        assertEquals(ArchiveStudyLookupException.Reason.ARCHIVE_INVALID_RESPONSE, ex.reason());
    }

    @Test
    void missing_content_type_throws_invalid_response() {
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies", exchange -> {
            respond(exchange, 200, null, MATCHING_STUDY_BODY);
        });

        ArchiveStudyLookupException ex = assertThrows(
            ArchiveStudyLookupException.class,
            () -> gateway(Duration.ofSeconds(2)).exists(STUDY_REF, "token")
        );
        assertEquals(ArchiveStudyLookupException.Reason.ARCHIVE_INVALID_RESPONSE, ex.reason());
    }

    @Test
    void invalid_json_throws_invalid_response() {
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies", exchange -> {
            respond(exchange, 200, DICOM_JSON, "corrupted-non-json-body");
        });

        ArchiveStudyLookupException ex = assertThrows(
            ArchiveStudyLookupException.class,
            () -> gateway(Duration.ofSeconds(2)).exists(STUDY_REF, "token")
        );
        assertEquals(ArchiveStudyLookupException.Reason.ARCHIVE_INVALID_RESPONSE, ex.reason());
    }

    @Test
    void mismatched_uid_throws_invalid_response() {
        String mismatchedBody = """
            [
              {
                "0020000D": {
                  "vr": "UI",
                  "Value": ["1.2.840.113619.2.55.3.604688416.741.999"]
                }
              }
            ]
            """;

        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies", exchange -> {
            respond(exchange, 200, DICOM_JSON, mismatchedBody);
        });

        ArchiveStudyLookupException ex = assertThrows(
            ArchiveStudyLookupException.class,
            () -> gateway(Duration.ofSeconds(2)).exists(STUDY_REF, "token")
        );
        assertEquals(ArchiveStudyLookupException.Reason.ARCHIVE_INVALID_RESPONSE, ex.reason());
    }

    @Test
    void unexpected_status_throws_invalid_response() {
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies", exchange -> {
            respond(exchange, 400, "application/json", "{\"error\":\"bad request\"}");
        });

        ArchiveStudyLookupException ex = assertThrows(
            ArchiveStudyLookupException.class,
            () -> gateway(Duration.ofSeconds(2)).exists(STUDY_REF, "token")
        );
        assertEquals(ArchiveStudyLookupException.Reason.ARCHIVE_INVALID_RESPONSE, ex.reason());
    }

    @Test
    void unexpected_runtime_bug_propagates_unwrapped() {
        HttpClient broken = mock(HttpClient.class);
        try {
            when(broken.send(any(), any())).thenThrow(new IllegalStateException("internal bug"));
        } catch (Exception e) {
            throw new AssertionError(e);
        }

        HttpStudyExistenceGateway gateway = new HttpStudyExistenceGateway(
            baseUrl,
            Duration.ofSeconds(2),
            new ReportQidoQueryBuilder(),
            new ReportQidoResponseParser(),
            broken
        );

        assertThrows(IllegalStateException.class, () -> gateway.exists(STUDY_REF, "token"));
    }

    @Test
    void null_guards_for_arguments() {
        HttpStudyExistenceGateway gateway = gateway(Duration.ofSeconds(2));
        assertThrows(NullPointerException.class, () -> gateway.exists(null, "token"));
        assertThrows(NullPointerException.class, () -> gateway.exists(STUDY_REF, null));
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

    private HttpStudyExistenceGateway gateway(Duration timeout) {
        return new HttpStudyExistenceGateway(
            baseUrl,
            timeout,
            new ReportQidoQueryBuilder(),
            new ReportQidoResponseParser(),
            HttpClient.newBuilder().connectTimeout(timeout).build()
        );
    }
}
