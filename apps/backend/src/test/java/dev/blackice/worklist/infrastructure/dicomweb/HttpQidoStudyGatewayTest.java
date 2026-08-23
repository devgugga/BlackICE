package dev.blackice.worklist.infrastructure.dicomweb;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.blackice.worklist.application.exception.ArchiveSearchException;
import dev.blackice.worklist.application.input.StudySearchRequest;
import dev.blackice.worklist.application.result.StudySummary;
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

class HttpQidoStudyGatewayTest {

    private static final String VALID_QIDO_BODY = """
        [
          {
            "0020000D": {
              "vr": "UI",
              "Value": ["1.2.3"]
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
    void sends_one_authenticated_qido_request_and_parses_response() {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Headers> headers = new AtomicReference<>();
        AtomicReference<String> rawQuery = new AtomicReference<>();
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies", exchange -> {
            calls.incrementAndGet();
            headers.set(exchange.getRequestHeaders());
            rawQuery.set(exchange.getRequestURI().getRawQuery());
            respond(exchange, 200, "application/dicom+json", VALID_QIDO_BODY);
        });

        List<StudySummary> result = gateway(Duration.ofSeconds(2))
            .search(request(), 21, "user-token");

        assertEquals(1, calls.get());
        assertEquals("Bearer user-token", headers.get().getFirst("Authorization"));
        assertEquals("application/dicom+json", headers.get().getFirst("Accept"));
        assertNotNull(rawQuery.get());
        assertTrue(URLDecoder.decode(rawQuery.get(), StandardCharsets.UTF_8).contains("limit=21"));
        assertEquals(1, result.size());
        assertEquals("1.2.3", result.get(0).studyInstanceUid());
    }

    @Test
    void http_413_payload_too_large_throws_query_too_broad() {
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies", exchange -> {
            respond(exchange, 413, "text/plain", "query too broad secret-body-content");
        });

        ArchiveSearchException ex = assertThrows(
            ArchiveSearchException.class,
            () -> gateway(Duration.ofSeconds(2)).search(request(), 21, "secret-token")
        );

        assertEquals(ArchiveSearchException.Reason.QUERY_TOO_BROAD, ex.reason());
        assertEquals("QUERY_TOO_BROAD", ex.getMessage());
        assertFalse(ex.getMessage().contains("secret"));
    }

    @Test
    void non_2xx_http_status_throws_http_status_without_leaking_body() {
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies", exchange -> {
            respond(exchange, 500, "application/json", "{\"secret\":\"confidential-patient-data\"}");
        });

        ArchiveSearchException ex = assertThrows(
            ArchiveSearchException.class,
            () -> gateway(Duration.ofSeconds(2)).search(request(), 21, "secret-token")
        );

        assertEquals(ArchiveSearchException.Reason.HTTP_STATUS, ex.reason());
        assertEquals("HTTP_STATUS", ex.getMessage());
        assertFalse(ex.getMessage().contains("confidential"));
        assertFalse(ex.getMessage().contains("secret"));
    }

    @Test
    void wrong_content_type_throws_invalid_response() {
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies", exchange -> {
            respond(exchange, 200, "text/html", VALID_QIDO_BODY);
        });

        ArchiveSearchException ex = assertThrows(
            ArchiveSearchException.class,
            () -> gateway(Duration.ofSeconds(2)).search(request(), 21, "token")
        );

        assertEquals(ArchiveSearchException.Reason.INVALID_RESPONSE, ex.reason());
        assertEquals("INVALID_RESPONSE", ex.getMessage());
    }

    @Test
    void missing_content_type_throws_invalid_response() {
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies", exchange -> {
            respond(exchange, 200, null, VALID_QIDO_BODY);
        });

        ArchiveSearchException ex = assertThrows(
            ArchiveSearchException.class,
            () -> gateway(Duration.ofSeconds(2)).search(request(), 21, "token")
        );

        assertEquals(ArchiveSearchException.Reason.INVALID_RESPONSE, ex.reason());
        assertEquals("INVALID_RESPONSE", ex.getMessage());
    }

    @Test
    void invalid_json_throws_invalid_response() {
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies", exchange -> {
            respond(exchange, 200, "application/dicom+json", "corrupted-non-json-content");
        });

        ArchiveSearchException ex = assertThrows(
            ArchiveSearchException.class,
            () -> gateway(Duration.ofSeconds(2)).search(request(), 21, "token")
        );

        assertEquals(ArchiveSearchException.Reason.INVALID_RESPONSE, ex.reason());
        assertEquals("INVALID_RESPONSE", ex.getMessage());
    }

    @Test
    void missing_study_instance_uid_throws_invalid_response() {
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies", exchange -> {
            String bodyWithoutUid = """
                [
                  {
                    "00100010": {
                      "vr": "PN",
                      "Value": ["DOE^JOHN"]
                    }
                  }
                ]
                """;
            respond(exchange, 200, "application/dicom+json", bodyWithoutUid);
        });

        ArchiveSearchException ex = assertThrows(
            ArchiveSearchException.class,
            () -> gateway(Duration.ofSeconds(2)).search(request(), 21, "token")
        );

        assertEquals(ArchiveSearchException.Reason.INVALID_RESPONSE, ex.reason());
        assertEquals("INVALID_RESPONSE", ex.getMessage());
    }

    @Test
    void timeout_throws_archive_search_exception_with_timeout_reason() {
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies", exchange -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {
            }
            respond(exchange, 200, "application/dicom+json", VALID_QIDO_BODY);
        });

        ArchiveSearchException ex = assertThrows(
            ArchiveSearchException.class,
            () -> gateway(Duration.ofMillis(50)).search(request(), 21, "token")
        );

        assertEquals(ArchiveSearchException.Reason.TIMEOUT, ex.reason());
        assertEquals("TIMEOUT", ex.getMessage());
    }

    @Test
    void connection_failure_throws_archive_search_exception_with_connection_reason() {
        server.stop(0);

        ArchiveSearchException ex = assertThrows(
            ArchiveSearchException.class,
            () -> gateway(Duration.ofSeconds(2)).search(request(), 21, "token")
        );

        assertEquals(ArchiveSearchException.Reason.CONNECTION, ex.reason());
        assertEquals("CONNECTION", ex.getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    void interrupted_execution_throws_connection_reason_and_restores_interrupt_flag() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenThrow(new InterruptedException("simulated interrupt"));

        HttpQidoStudyGateway gateway = new HttpQidoStudyGateway(
            baseUrl,
            Duration.ofSeconds(2),
            new QidoQueryBuilder(),
            new QidoStudyResponseParser(),
            mockClient
        );

        try {
            ArchiveSearchException ex = assertThrows(
                ArchiveSearchException.class,
                () -> gateway.search(request(), 21, "token")
            );

            assertEquals(ArchiveSearchException.Reason.CONNECTION, ex.reason());
            assertEquals("CONNECTION", ex.getMessage());
            assertTrue(Thread.currentThread().isInterrupted(), "Interrupt flag must be restored");
        } finally {
            // Clear interrupt status for subsequent tests
            Thread.interrupted();
        }
    }

    @Test
    void http_204_no_content_returns_empty_list() {
        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies", exchange -> {
            respond(exchange, 204, null, "");
        });

        List<StudySummary> result = gateway(Duration.ofSeconds(2))
            .search(request(), 21, "user-token");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void null_guards_for_search_arguments() {
        HttpQidoStudyGateway gateway = gateway(Duration.ofSeconds(2));
        assertThrows(NullPointerException.class, () -> gateway.search(null, 21, "token"));
        assertThrows(NullPointerException.class, () -> gateway.search(request(), 21, null));
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

    private StudySearchRequest request() {
        return new StudySearchRequest(null, null, null, null, null, 20, 0);
    }

    private HttpQidoStudyGateway gateway(Duration timeout) {
        return new HttpQidoStudyGateway(
            baseUrl,
            timeout,
            new QidoQueryBuilder(),
            new QidoStudyResponseParser(),
            HttpClient.newBuilder().connectTimeout(timeout).build()
        );
    }
}
