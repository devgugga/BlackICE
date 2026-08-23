package dev.blackice.ingest.api;

import java.util.List;

import dev.blackice.ingest.application.result.IngestResult;
import dev.blackice.shared.api.problem.ProblemResponseFactory;
import dev.blackice.shared.api.problem.generated.ProblemExtensions;
import dev.blackice.shared.api.problem.generated.ProblemType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

/**
 * Decide se um lote de ingestão é resultado ou problema.
 *
 * <p>Sucesso completo e parcial continuam sendo {@code 200} com o
 * {@link IngestResult}: a operação aconteceu e tem o que relatar. Só vira
 * problema quando nada pôde ser feito — nenhum arquivo passou pela validação
 * local, ou todos os estudos válidos falharam por indisponibilidade do Archive.
 */
@ApplicationScoped
public class IngestResponseMapper {

    /** Código interno com que o caso de uso marca um estudo não armazenado. */
    private static final String ARCHIVE_UNAVAILABLE = "ARCHIVE_UNAVAILABLE";

    private final ProblemResponseFactory problems;

    @Inject
    public IngestResponseMapper(ProblemResponseFactory problems) {
        this.problems = problems;
    }

    public Response toResponse(IngestResult result) {
        if (result.summary().received() > 0 && result.summary().locallyValid() == 0) {
            return problems.response(ProblemType.API_DICOM_VALIDATION_FAILED, violations(result));
        }
        if (allValidStudiesUnavailable(result)) {
            return problems.response(ProblemType.API_ARCHIVE_UNAVAILABLE);
        }
        return Response.ok(result).build();
    }

    /**
     * Converte as rejeições locais na extensão pública.
     *
     * <p>O {@code filename} fica de fora de propósito: nomes de arquivo podem
     * conter informação identificável, e o consumidor associa {@code itemIndex}
     * aos arquivos que já mantém localmente.
     */
    private static ProblemExtensions violations(IngestResult result) {
        List<ProblemExtensions.Violation> violations = result.locallyRejectedFiles().stream()
            .map(rejected -> new ProblemExtensions.Violation(
                rejected.itemIndex(),
                rejected.code().name(),
                rejected.message()))
            .toList();
        return new ProblemExtensions.DicomValidationViolations(violations);
    }

    private static boolean allValidStudiesUnavailable(IngestResult result) {
        return result.summary().locallyValid() > 0
            && result.summary().archiveAccepted() == 0
            && !result.studies().isEmpty()
            && result.studies().stream().allMatch(study -> ARCHIVE_UNAVAILABLE.equals(study.errorCode()));
    }
}
