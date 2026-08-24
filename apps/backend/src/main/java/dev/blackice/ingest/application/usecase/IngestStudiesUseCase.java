package dev.blackice.ingest.application.usecase;

import dev.blackice.ingest.application.port.DicomArchiveGateway;
import dev.blackice.ingest.application.port.DicomBatchValidator;
import dev.blackice.ingest.application.port.TaskExecutorFactory;
import dev.blackice.ingest.application.exception.ArchiveUnavailableException;
import dev.blackice.ingest.application.input.UploadedDicom;
import dev.blackice.ingest.application.result.IngestResult;
import dev.blackice.ingest.application.result.StowInstanceResult;
import dev.blackice.ingest.application.result.StowStudyResult;
import dev.blackice.ingest.application.validation.DicomBatchValidation;
import dev.blackice.ingest.application.validation.DicomValidationIssue;
import dev.blackice.ingest.application.validation.ValidatedDicom;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Coordinates validation and storage of uploaded DICOM studies through application ports.
 *
 * <p>It preserves the archive-assigned UIDs found during validation and reports partial STOW results.</p>
 */
@ApplicationScoped
public class IngestStudiesUseCase {

    private final DicomBatchValidator validator;
    private final DicomArchiveGateway gateway;
    private final TaskExecutorFactory executorFactory;
    private final int maxConcurrentStudies;

    @Inject
    public IngestStudiesUseCase(
        DicomBatchValidator validator,
        DicomArchiveGateway gateway,
        TaskExecutorFactory executorFactory,
        @ConfigProperty(name = "blackice.ingest.max-concurrent-studies", defaultValue = "1")
        int maxConcurrentStudies
    ) {
        this.validator = Objects.requireNonNull(validator, "validator must not be null");
        this.gateway = Objects.requireNonNull(gateway, "gateway must not be null");
        this.executorFactory = Objects.requireNonNull(executorFactory, "executorFactory must not be null");
        this.maxConcurrentStudies = Math.max(1, maxConcurrentStudies);
    }

    public IngestStudiesUseCase(
        DicomBatchValidator validator,
        DicomArchiveGateway gateway,
        int maxConcurrentStudies
    ) {
        this(validator, gateway, Executors::newVirtualThreadPerTaskExecutor, maxConcurrentStudies);
    }

    public IngestResult ingest(List<UploadedDicom> uploads, String accessToken) {
        InterruptState interruptState = new InterruptState();
        try {
            int received = uploads != null ? uploads.size() : 0;
            if (uploads == null || uploads.isEmpty()) {
                IngestResult.Summary summary = new IngestResult.Summary(0, 0, 0, 0, 0);
                return new IngestResult(
                    IngestResult.Outcome.FAILED,
                    summary,
                    List.of(),
                    List.of()
                );
            }

            DicomBatchValidation validation = validator.validate(uploads);
            Map<String, List<ValidatedDicom>> validStudies = validation.validStudies();
            List<DicomValidationIssue> issues = validation.issues();

            List<IngestResult.RejectedFile> locallyRejectedFiles = issues.stream()
                .map(issue -> new IngestResult.RejectedFile(
                    issue.itemIndex(), issue.filename(), issue.code(), issue.message()))
                .toList();

            int locallyRejected = locallyRejectedFiles.size();
            int locallyValid = validStudies.values().stream().mapToInt(List::size).sum();

            if (validStudies.isEmpty()) {
                IngestResult.Summary summary = new IngestResult.Summary(
                    received,
                    locallyValid,
                    locallyRejected,
                    0,
                    0
                );
                return new IngestResult(IngestResult.Outcome.FAILED, summary, List.of(), locallyRejectedFiles);
            }

            List<StudyAttempt> attempts;
            ExecutorService executor = executorFactory.create();
            try {
                Semaphore permits = new Semaphore(maxConcurrentStudies, true);
                List<StudyTask> tasks = new ArrayList<>();
                List<StudyAttempt> unsubmittedAttempts = new ArrayList<>();
                List<Map.Entry<String, List<ValidatedDicom>>> studies = new ArrayList<>(validStudies.entrySet());
                for (int studyIndex = 0; studyIndex < studies.size(); studyIndex++) {
                    Map.Entry<String, List<ValidatedDicom>> entry = studies.get(studyIndex);
                    String studyUid = entry.getKey();
                    List<ValidatedDicom> files = entry.getValue();
                    try {
                        permits.acquire();
                    } catch (InterruptedException e) {
                        interruptState.mark();
                        for (int unsubmittedIndex = studyIndex; unsubmittedIndex < studies.size(); unsubmittedIndex++) {
                            unsubmittedAttempts.add(interruptedAttempt(studies.get(unsubmittedIndex).getKey()));
                        }
                        break;
                    }
                    Future<StudyAttempt> future = executor.submit(() -> {
                        try {
                            return store(studyUid, files, accessToken);
                        } finally {
                            permits.release();
                        }
                    });
                    tasks.add(new StudyTask(studyUid, future));
                }
                executor.shutdown();
                List<StudyAttempt> joinedAttempts = joinInSubmissionOrder(tasks, interruptState);
                attempts = new ArrayList<>(joinedAttempts.size() + unsubmittedAttempts.size());
                attempts.addAll(joinedAttempts);
                attempts.addAll(unsubmittedAttempts);
            } finally {
                executor.shutdown();
                interruptState.mark(awaitTermination(executor));
            }

            List<IngestResult.StudyResult> studyResults = new ArrayList<>();
            int totalAccepted = 0;
            int totalRejected = 0;

            for (StudyAttempt attempt : attempts) {
                String studyUid = attempt.studyInstanceUid();
                List<ValidatedDicom> studyFiles = validStudies.get(studyUid);
                int studyFileCount = studyFiles != null ? studyFiles.size() : 0;

                if (attempt.failure() != null || attempt.result() == null) {
                    totalRejected += studyFileCount;
                    studyResults.add(new IngestResult.StudyResult(
                        studyUid,
                        IngestResult.StudyStatus.FAILED,
                        List.of(),
                        failureReason(attempt)
                    ));
                } else {
                    StowStudyResult result = attempt.result();
                    List<IngestResult.InstanceResult> instanceResults = new ArrayList<>();
                    int studyAccepted = 0;
                    int studyRejected = 0;

                    for (StowInstanceResult inst : result.instances()) {
                        instanceResults.add(new IngestResult.InstanceResult(
                            inst.sopInstanceUid(),
                            inst.status(),
                            inst.reason()
                        ));
                        if (inst.status() == StowInstanceResult.Status.ACCEPTED
                            || inst.status() == StowInstanceResult.Status.WARNING) {
                            studyAccepted++;
                        } else {
                            studyRejected++;
                        }
                    }

                    totalAccepted += studyAccepted;
                    totalRejected += studyRejected;

                    IngestResult.StudyStatus status;
                    if (instanceResults.isEmpty()) {
                        status = IngestResult.StudyStatus.FAILED;
                    } else if (studyAccepted == instanceResults.size()) {
                        status = IngestResult.StudyStatus.COMPLETE;
                    } else if (studyAccepted == 0) {
                        status = IngestResult.StudyStatus.FAILED;
                    } else {
                        status = IngestResult.StudyStatus.PARTIAL;
                    }

                    studyResults.add(new IngestResult.StudyResult(
                        studyUid,
                        status,
                        instanceResults,
                        null
                    ));
                }
            }

            IngestResult.Summary summary = new IngestResult.Summary(
                received,
                locallyValid,
                locallyRejected,
                totalAccepted,
                totalRejected
            );

            IngestResult.Outcome outcome;
            if (totalAccepted == received && locallyRejected == 0) {
                outcome = IngestResult.Outcome.COMPLETE;
            } else if (totalAccepted == 0) {
                outcome = IngestResult.Outcome.FAILED;
            } else {
                outcome = IngestResult.Outcome.PARTIAL;
            }

            return new IngestResult(outcome, summary, studyResults, locallyRejectedFiles);
        } finally {
            if (interruptState.wasInterrupted()) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Stable internal reason for a study that was not stored.
     *
     * <p>It is not a public code: the API boundary translates it into a
     * catalogued problem. Keeping the reason distinguishes "never reached the
     * archive" from "the archive answered but the response was unusable".
     */
    private static String failureReason(StudyAttempt attempt) {
        return attempt.failure() != null ? attempt.failure().reason().name() : "UNKNOWN";
    }

    private StudyAttempt store(String studyUid, List<ValidatedDicom> files, String accessToken) {
        try {
            StowStudyResult result = gateway.storeStudy(studyUid, files, accessToken);
            return new StudyAttempt(studyUid, result, null);
        } catch (ArchiveUnavailableException e) {
            // Only a known archive failure becomes a failed study attempt.
            // Unexpected implementation bugs propagate to the HTTP 500 fallback.
            return new StudyAttempt(studyUid, null, e);
        }
    }

    private List<StudyAttempt> joinInSubmissionOrder(List<StudyTask> tasks, InterruptState interruptState) {
        List<StudyAttempt> attempts = new ArrayList<>(tasks.size());
        for (StudyTask task : tasks) {
            boolean collected = false;
            while (!collected) {
                if (Thread.interrupted()) {
                    interruptState.mark();
                }
                try {
                    attempts.add(task.future().get());
                    collected = true;
                } catch (InterruptedException e) {
                    interruptState.mark();
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof ArchiveUnavailableException unavailable) {
                        attempts.add(new StudyAttempt(task.studyInstanceUid(), null, unavailable));
                        collected = true;
                        continue;
                    }
                    // Do not disguise an implementation bug as archive unavailability.
                    if (cause instanceof RuntimeException runtime) {
                        throw runtime;
                    }
                    if (cause instanceof Error error) {
                        throw error;
                    }
                    throw new IllegalStateException("Unexpected study storage failure", cause);
                }
            }
        }
        if (Thread.interrupted()) {
            interruptState.mark();
        }
        return List.copyOf(attempts);
    }

    private static StudyAttempt interruptedAttempt(String studyInstanceUid) {
        return new StudyAttempt(
            studyInstanceUid,
            null,
            new ArchiveUnavailableException(ArchiveUnavailableException.Reason.INTERRUPTED, null)
        );
    }

    private static boolean awaitTermination(ExecutorService executor) {
        boolean interrupted = false;
        while (!executor.isTerminated()) {
            try {
                executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        return interrupted;
    }

    private record StudyTask(String studyInstanceUid, Future<StudyAttempt> future) {}

    private static final class InterruptState {
        private boolean interrupted;

        void mark() {
            interrupted = true;
        }

        void mark(boolean interrupted) {
            this.interrupted |= interrupted;
        }

        boolean wasInterrupted() {
            return interrupted;
        }
    }

    private record StudyAttempt(
        String studyInstanceUid,
        StowStudyResult result,
        ArchiveUnavailableException failure
    ) {}
}
