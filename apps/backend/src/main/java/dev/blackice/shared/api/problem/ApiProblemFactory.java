package dev.blackice.shared.api.problem;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.blackice.shared.api.problem.generated.ProblemExtensions;
import dev.blackice.shared.api.problem.generated.ProblemType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Constrói o corpo RFC 9457 de um tipo catalogado.
 *
 * <p>Os textos vêm sempre do catálogo, nunca de uma exceção, e a extensão é
 * validada contra o tipo antes de virar payload. Nada aqui aceita dado de
 * paciente: ver {@code docs/domains/problem-catalog/security.md}.
 */
@ApplicationScoped
public class ApiProblemFactory {

    private final TraceContext traceContext;

    @Inject
    public ApiProblemFactory(TraceContext traceContext) {
        this.traceContext = traceContext;
    }

    public ApiProblem create(ProblemType type, ProblemExtensions extensions) {
        if (type.scope() != ProblemType.ProblemScope.API) {
            throw new IllegalArgumentException(
                "tipo " + type.code() + " não é uma resposta HTTP e não vira Problem Details");
        }
        if (!extensions.appliesTo(type)) {
            throw new IllegalArgumentException(
                "extensão " + extensions.getClass().getSimpleName() + " não pertence ao tipo " + type.code());
        }

        return new ApiProblem(
            type.type(),
            type.title(),
            type.httpStatus(),
            type.detail(),
            type.code(),
            traceContext.traceId(),
            members(extensions));
    }

    /** Membros adicionais que a extensão publica no nível raiz do problema. */
    private static Map<String, Object> members(ProblemExtensions extensions) {
        return switch (extensions) {
            case ProblemExtensions.None ignored -> Map.of();
            case ProblemExtensions.DicomValidationViolations violations ->
                Map.of("violations", violations.violations().stream().map(ApiProblemFactory::violation).toList());
        };
    }

    private static Map<String, Object> violation(ProblemExtensions.Violation violation) {
        Map<String, Object> member = new LinkedHashMap<>();
        member.put("itemIndex", violation.itemIndex());
        member.put("code", violation.code());
        member.put("message", violation.message());
        return member;
    }
}
