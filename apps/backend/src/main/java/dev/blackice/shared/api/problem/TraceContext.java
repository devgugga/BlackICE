package dev.blackice.shared.api.problem;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Leitura do span OpenTelemetry ativo.
 *
 * <p>O contexto canônico vem do {@code traceparent} W3C recebido, ou de um trace
 * novo criado pelo Quarkus. Um {@code X-Trace-ID} enviado pelo cliente nunca
 * chega aqui: ele não substitui o contexto.
 *
 * <p>Esta classe é a única porta para o TraceID na fronteira HTTP. Domínio e
 * aplicação não a conhecem, e não guardam TraceID em exceções.
 */
@ApplicationScoped
public class TraceContext {

    /** TraceID de 32 hexadecimais, ou {@code null} quando não há trace válido. */
    public String traceId() {
        SpanContext context = Span.current().getSpanContext();
        return context.isValid() ? context.getTraceId() : null;
    }

    /** SpanID de 16 hexadecimais, ou {@code null} quando não há trace válido. */
    public String spanId() {
        SpanContext context = Span.current().getSpanContext();
        return context.isValid() ? context.getSpanId() : null;
    }
}
