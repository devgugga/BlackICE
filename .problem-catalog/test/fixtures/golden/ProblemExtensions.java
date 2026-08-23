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

    /** Indica se esta variante pertence ao tipo, conforme o catálogo. */
    boolean appliesTo(ProblemType type);

    /** Ausência de membros adicionais; aceita por qualquer tipo. */
    record None() implements ProblemExtensions {

        @Override
        public boolean appliesTo(ProblemType type) {
            return true;
        }
    }

    /** Instância canônica para tipos sem extensão. */
    static ProblemExtensions none() {
        return new None();
    }

    /** Membros adicionais definidos por extensions/dicom-validation-violations.schema.json. */
    record DicomValidationViolations(List<Violation> violations) implements ProblemExtensions {

        @Override
        public boolean appliesTo(ProblemType type) {
            return type == ProblemType.API_DICOM_VALIDATION_FAILED;
        }
    }

    /** Item tipado de uma extensão do catálogo. */
    record Violation(int itemIndex, String code, String message) {
    }
}
