package dev.blackice.ingest.api;

import java.util.List;

import dev.blackice.ingest.application.exception.ArchiveUnavailableException.Reason;
import dev.blackice.ingest.application.result.IngestResult;
import dev.blackice.shared.api.problem.ProblemResponseFactory;
import dev.blackice.shared.api.problem.ApiFailureLogger;
import dev.blackice.shared.api.problem.generated.ProblemExtensions;
import dev.blackice.shared.api.problem.generated.ProblemType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

/** Decides whether an ingest batch is an HTTP 200 result or a catalogued problem. */
@ApplicationScoped
public class IngestResponseMapper {

    private final ProblemResponseFactory problems;
    private final ApiFailureLogger failureLogger;

    @Inject
    public IngestResponseMapper(ProblemResponseFactory problems, ApiFailureLogger failureLogger) {
        this.problems = problems;
        this.failureLogger = failureLogger;
    }

    public Response toResponse(IngestResult result) {
        if (result.summary().received() > 0 && result.summary().locallyValid() == 0) {
            ProblemType type = ProblemType.API_DICOM_VALIDATION_FAILED;
            failureLogger.known(type, "POST", "/api/studies", ApiFailureLogger.Reason.LOCAL_VALIDATION);
            return problems.response(type, violations(result));
        }
        if (hasConfirmableInstances(result)) {
            return Response.ok(result).build();
        }
        if (hasFailure(result, Reason.OUTCOME_UNKNOWN)) {
            return archiveProblem(ProblemType.API_ARCHIVE_OUTCOME_UNKNOWN, ApiFailureLogger.Reason.OUTCOME_UNKNOWN);
        }
        Reason unusableReason = firstFailure(result, Reason.HTTP_STATUS, Reason.INVALID_RESPONSE);
        if (unusableReason != null) {
            return archiveProblem(ProblemType.API_ARCHIVE_RESPONSE_INVALID,
                ApiFailureLogger.Reason.valueOf(unusableReason.name()));
        }
        Reason unavailableReason = firstFailure(result, Reason.TIMEOUT, Reason.CONNECTION, Reason.INTERRUPTED);
        ApiFailureLogger.Reason safeReason = unavailableReason == null
            ? ApiFailureLogger.Reason.UNCLASSIFIED
            : ApiFailureLogger.Reason.valueOf(unavailableReason.name());
        return archiveProblem(ProblemType.API_ARCHIVE_UNAVAILABLE, safeReason);
    }

    /**
     * Converts local rejections to the public extension.
     *
     * <p>The filename is deliberately excluded because it may contain identifiable data. The
     * consumer correlates the item index with files it already holds locally.</p>
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

    /** Any reported instance, including a confirmed rejection, makes this an HTTP 200 result. */
    private static boolean hasConfirmableInstances(IngestResult result) {
        return result.studies().stream().anyMatch(study -> !study.instances().isEmpty());
    }

    private static boolean hasFailure(IngestResult result, Reason reason) {
        return result.studies().stream().anyMatch(study -> reason.name().equals(study.errorCode()));
    }

    private Response archiveProblem(ProblemType type, ApiFailureLogger.Reason reason) {
        failureLogger.known(type, "POST", "/api/studies", reason);
        return problems.response(type);
    }

    private static Reason firstFailure(IngestResult result, Reason... reasons) {
        for (Reason reason : reasons) {
            if (hasFailure(result, reason)) {
                return reason;
            }
        }
        return null;
    }
}
