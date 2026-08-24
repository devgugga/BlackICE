package dev.blackice.shared.infrastructure.telemetry;

import java.net.http.HttpRequest;

import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;

/**
 * Injects W3C trace context into Archive calls.
 *
 * <p>While DICOMweb adapters use {@link java.net.http.HttpClient}, Quarkus automatic
 * instrumentation does not reach the request. Explicit propagation keeps the trace
 * connected from the browser through DCM4CHEE.
 *
 * <p>Only W3C trace headers cross this boundary. Baggage and internal correlation
 * headers such as {@code X-Trace-ID} and {@code X-Request-ID} are never sent.
 *
 * <p>This class is stateless and reads the current context on every call.</p>
 */
public class W3cTraceContextInjector {

    private static final TextMapPropagator TRACE_CONTEXT = W3CTraceContextPropagator.getInstance();

    public void inject(HttpRequest.Builder builder) {
        TRACE_CONTEXT.inject(Context.current(), builder, HttpRequest.Builder::header);
    }
}
