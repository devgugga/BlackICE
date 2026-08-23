package dev.blackice.ingest.api;

import java.util.List;
import java.util.Set;

import dev.blackice.ingest.application.exception.ArchiveUnavailableException.Reason;
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

    /**
     * Razões internas em que o Archive não chegou a receber, ou não chegou a
     * processar, os arquivos. Só elas são indisponibilidade de verdade.
     */
    private static final Set<String> NEVER_REACHED_THE_ARCHIVE =
        Set.of(Reason.TIMEOUT.name(), Reason.CONNECTION.name(), Reason.INTERRUPTED.name());

    /**
     * Razões em que o Archive respondeu, mas a resposta não pôde ser usada. As
     * instâncias podem já estar gravadas, então isto não é indisponibilidade.
     */
    private static final Set<String> ANSWERED_BUT_UNUSABLE =
        Set.of(Reason.HTTP_STATUS.name(), Reason.INVALID_RESPONSE.name());

    private final ProblemResponseFactory problems;

    @Inject
    public IngestResponseMapper(ProblemResponseFactory problems) {
        this.problems = problems;
    }

    public Response toResponse(IngestResult result) {
        if (result.summary().received() > 0 && result.summary().locallyValid() == 0) {
            return problems.response(ProblemType.API_DICOM_VALIDATION_FAILED, violations(result));
        }
        if (nothingWasStored(result)) {
            return problems.response(archiveProblem(result));
        }
        return Response.ok(result).build();
    }

    /**
     * Distingue indisponibilidade de resposta inutilizável.
     *
     * <p>Um `2xx` que não pôde ser interpretado significa que o Archive
     * respondeu e pode já ter gravado as instâncias. Chamar isso de
     * indisponibilidade levaria o usuário a reenviar o mesmo lote e duplicar a
     * ingestão. É a mesma distinção que a Worklist faz para o QIDO.
     */
    private static ProblemType archiveProblem(IngestResult result) {
        boolean anyAnsweredButUnusable = result.studies().stream()
            .anyMatch(study -> ANSWERED_BUT_UNUSABLE.contains(study.errorCode()));
        return anyAnsweredButUnusable
            ? ProblemType.API_ARCHIVE_RESPONSE_INVALID
            : ProblemType.API_ARCHIVE_UNAVAILABLE;
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

    /** Verdadeiro quando havia arquivos válidos e nenhum deles chegou a ser aceito. */
    private static boolean nothingWasStored(IngestResult result) {
        return result.summary().locallyValid() > 0
            && result.summary().archiveAccepted() == 0
            && !result.studies().isEmpty()
            && result.studies().stream().allMatch(study ->
                NEVER_REACHED_THE_ARCHIVE.contains(study.errorCode())
                    || ANSWERED_BUT_UNUSABLE.contains(study.errorCode()));
    }
}
