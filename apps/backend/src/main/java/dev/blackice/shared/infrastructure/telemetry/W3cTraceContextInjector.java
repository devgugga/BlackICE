package dev.blackice.shared.infrastructure.telemetry;

import java.net.http.HttpRequest;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;

/**
 * Injeta o contexto de trace W3C nas chamadas ao Archive.
 *
 * <p>Enquanto os adapters DICOMweb usarem {@link java.net.http.HttpClient}, a
 * instrumentação automática do Quarkus não alcança a requisição: a propagação
 * precisa ser explícita para que o TraceID continue do browser até o DCM4CHEE.
 *
 * <p>Só o {@code traceparent} atravessa. O {@code X-Trace-ID} é um detalhe da
 * resposta ao SPA e nunca é enviado ao Archive.
 *
 * <p>Sem estado: lê o contexto corrente a cada chamada, então os adapters o
 * instanciam diretamente em vez de recebê-lo por injeção.
 */
public class W3cTraceContextInjector {

    public void inject(HttpRequest.Builder builder) {
        GlobalOpenTelemetry.getPropagators().getTextMapPropagator().inject(
            Context.current(), builder, HttpRequest.Builder::header);
    }
}
