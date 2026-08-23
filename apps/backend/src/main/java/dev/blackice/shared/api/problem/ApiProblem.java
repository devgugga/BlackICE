package dev.blackice.shared.api.problem;

import java.net.URI;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;

/**
 * Corpo de um erro HTTP conforme RFC 9457.
 *
 * <p>Os membros são exatamente os do contrato publicado. {@code instance} é
 * omitido nesta versão, e {@code retryPolicy} não é repetido no corpo: o cliente
 * o resolve pelo tipo, a partir do catálogo gerado.
 *
 * <p>{@code extensions} é serializado no nível raiz, ao lado de {@code traceId},
 * e nunca aparece como um campo aninhado chamado {@code extensions}.
 *
 * @param type    URN {@code urn:uuid} do tipo catalogado
 * @param title   texto público estável, vindo do catálogo
 * @param status  status HTTP da resposta
 * @param detail  detalhe público estável, vindo do catálogo
 * @param code    código legível do catálogo
 * @param traceId TraceID W3C da execução, ou {@code null} quando não há trace ativo
 * @param extensions membros adicionais tipados pela extensão do tipo
 */
public record ApiProblem(
        URI type,
        String title,
        int status,
        String detail,
        String code,
        String traceId,
        Map<String, Object> extensions) {

    @JsonAnyGetter
    @Override
    public Map<String, Object> extensions() {
        return extensions;
    }
}
