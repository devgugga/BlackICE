package dev.blackice.shared.infrastructure.telemetry;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import dev.blackice.ingest.application.validation.ValidatedDicom;
import dev.blackice.ingest.infrastructure.dicomweb.HttpDicomArchiveGateway;
import dev.blackice.ingest.infrastructure.dicomweb.StowResponseParser;
import dev.blackice.worklist.application.input.StudySearchRequest;
import dev.blackice.worklist.infrastructure.dicomweb.HttpQidoStudyGateway;
import dev.blackice.worklist.infrastructure.dicomweb.QidoQueryBuilder;
import dev.blackice.worklist.infrastructure.dicomweb.QidoStudyResponseParser;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class W3cTraceContextInjectorTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String SPAN_ID = "00f067aa0ba902b7";

    private final W3cTraceContextInjector injector = new W3cTraceContextInjector();

    /** Torna corrente um span com TraceID conhecido, como faria uma requisição real. */
    private static Scope knownSpan() {
        SpanContext spanContext = SpanContext.createFromRemoteParent(
            TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault());
        return Context.current().with(Span.wrap(spanContext)).makeCurrent();
    }

    @Test
    void the_active_trace_is_injected_as_a_w3c_traceparent() {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://archive.local/studies"));

        try (Scope ignored = knownSpan()) {
            injector.inject(builder);
        }

        Optional<String> traceparent = builder.build().headers().firstValue("traceparent");
        assertTrue(traceparent.isPresent(), "traceparent ausente na chamada ao Archive");
        assertTrue(traceparent.get().startsWith("00-" + TRACE_ID + "-"),
            "traceparent não continua o trace recebido: " + traceparent.get());
    }

    @Test
    void the_internal_correlation_header_is_never_sent_to_the_archive() {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://archive.local/studies"));

        try (Scope ignored = knownSpan()) {
            injector.inject(builder);
        }

        assertTrue(builder.build().headers().firstValue("X-Trace-ID").isEmpty());
        assertTrue(builder.build().headers().firstValue("X-Request-ID").isEmpty());
    }

    @Test
    void without_an_active_trace_no_header_is_invented() {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://archive.local/studies"));

        injector.inject(builder);

        assertEquals(Optional.empty(), builder.build().headers().firstValue("traceparent"));
    }

    @Test
    void qido_and_stow_calls_reach_the_archive_carrying_the_active_trace() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<Headers> qidoHeaders = new AtomicReference<>();
        AtomicReference<Headers> stowHeaders = new AtomicReference<>();
        String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/dcm4chee-arc/aets/DCM4CHEE/rs";

        server.createContext("/dcm4chee-arc/aets/DCM4CHEE/rs/studies", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                stowHeaders.set(exchange.getRequestHeaders());
                exchange.getRequestBody().readAllBytes();
            } else {
                qidoHeaders.set(exchange.getRequestHeaders());
            }
            byte[] body = "[]".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/dicom+json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        Path file = Files.createTempFile("trace", ".dcm");
        Files.write(file, new byte[] {1, 2, 3});
        try (Scope ignored = knownSpan()) {
            new HttpQidoStudyGateway(base, Duration.ofSeconds(5), new QidoQueryBuilder(),
                new QidoStudyResponseParser(), HttpClient.newHttpClient())
                .search(new StudySearchRequest(null, null, null, null, null, 20, 0), 21, "token");

            new HttpDicomArchiveGateway(base, Duration.ofSeconds(5), new StowResponseParser(),
                HttpClient.newHttpClient())
                .storeStudy("1.2.840.10008.1", List.of(new ValidatedDicom(
                    0, file, "trace.dcm", Files.size(file), "1.2.840.10008.1", "1.2.840.10008.1.1",
                    "1.2.840.10008.1.1.1", "1.2.840.10008.5.1.4.1.1.7", "h")), "token");
        } finally {
            server.stop(0);
            Files.deleteIfExists(file);
        }

        for (AtomicReference<Headers> captured : List.of(qidoHeaders, stowHeaders)) {
            String traceparent = captured.get().getFirst("traceparent");
            assertTrue(traceparent != null && traceparent.startsWith("00-" + TRACE_ID + "-"),
                "traceparent ausente ou divergente: " + traceparent);
            assertTrue(captured.get().getFirst("X-Trace-ID") == null,
                "X-Trace-ID nunca deve ser enviado ao Archive");
        }
    }

    @Test
    void injection_uses_the_globally_configured_propagators() {
        assertTrue(GlobalOpenTelemetry.getPropagators().getTextMapPropagator()
            .fields().contains("traceparent"));
    }
}
