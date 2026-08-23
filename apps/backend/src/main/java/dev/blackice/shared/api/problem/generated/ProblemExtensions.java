/*
 * DO NOT EDIT.
 *
 * Gerado por .problem-catalog a partir de docs/contracts/problems/catalog.json e docs/contracts/problems/extensions/.
 * Altere o catálogo e execute `pnpm generate` em .problem-catalog/.
 */

package dev.blackice.shared.api.problem.generated;

import java.util.List;

/**
 * Membros adicionais permitidos no nível raiz de um Problem Details.
 *
 * <p>A interface é selada: um tipo do catálogo só aceita a variante declarada
 * pelo seu {@code extensionsSchemaRef}, ou {@link None}.
 */
public sealed interface ProblemExtensions
        permits ProblemExtensions.None, ProblemExtensions.DicomValidationViolations {

    /** Ausência de membros adicionais. */
    record None() implements ProblemExtensions {
    }

    /** Instância canônica para tipos sem extensão. */
    static ProblemExtensions none() {
        return new None();
    }

    /** Membros adicionais definidos por extensions/dicom-validation-violations.schema.json. */
    record DicomValidationViolations(List<Violation> violations) implements ProblemExtensions {
    }

    /** Item tipado de uma extensão do catálogo. */
    record Violation(int itemIndex, String code, String message) {
    }
}
