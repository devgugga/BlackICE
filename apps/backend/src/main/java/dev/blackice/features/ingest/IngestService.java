package dev.blackice.features.ingest;

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

@ApplicationScoped
public class IngestService {

    private final DicomBatchValidator validator;
    private final DicomArchiveGateway gateway;
    private final int maxConcurrentStudies;

    @Inject
    public IngestService(
        DicomBatchValidator validator,
        DicomArchiveGateway gateway,
        @ConfigProperty(name = "blackice.ingest.max-concurrent-studies", defaultValue = "1")
        int maxConcurrentStudies
    ) {
        this.validator = Objects.requireNonNull(validator, "validator must not be null");
        this.gateway = Objects.requireNonNull(gateway, "gateway must not be null");
        this.maxConcurrentStudies = Math.max(1, maxConcurrentStudies);
    }

    public IngestExecution ingest(List<UploadedDicom> uploads, String accessToken) {
        int received = uploads != null ? uploads.size() : 0;
        if (uploads == null || uploads.isEmpty()) {
            IngestResponse.Summary summary = new IngestResponse.Summary(0, 0, 0, 0, 0);
            IngestResponse response = new IngestResponse(
                IngestResponse.Outcome.FAILED,
                summary,
                List.of(),
                List.of()
            );
            return new IngestExecution(200, response);
        }

        DicomBatchValidation validation = validator.validate(uploads);
        Map<String, List<ValidatedDicom>> validStudies = validation.validStudies();
        List<DicomValidationIssue> issues = validation.issues();

        List<IngestResponse.RejectedFile> locallyRejectedFiles = issues.stream()
            .map(issue -> new IngestResponse.RejectedFile(issue.filename(), issue.code(), issue.message()))
            .toList();

        int locallyRejected = locallyRejectedFiles.size();
        int locallyValid = validStudies.values().stream().mapToInt(List::size).sum();

        if (validStudies.isEmpty()) {
            IngestResponse.Summary summary = new IngestResponse.Summary(
                received,
                locallyValid,
                locallyRejected,
                0,
                0
            );
            IngestResponse.Outcome outcome = IngestResponse.Outcome.FAILED;
            int suggestedStatus = (received > 0 && locallyValid == 0) ? 422 : 200;
            IngestResponse response = new IngestResponse(outcome, summary, List.of(), locallyRejectedFiles);
            return new IngestExecution(suggestedStatus, response);
        }

        List<StudyAttempt> attempts = null;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Semaphore permits = new Semaphore(maxConcurrentStudies, true);
            List<StudyTask> tasks = new ArrayList<>();
            for (Map.Entry<String, List<ValidatedDicom>> entry : validStudies.entrySet()) {
                String studyUid = entry.getKey();
                List<ValidatedDicom> files = entry.getValue();
                try {
                    permits.acquire();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    attempts = List.of(new StudyAttempt(
                        studyUid,
                        null,
                        new ArchiveUnavailableException(ArchiveUnavailableException.Reason.INTERRUPTED, e)
                    ));
                    break;
                }
                Future<StudyAttempt> future = executor.submit(() -> {
                    try {
                        return store(studyUid, files, accessToken);
                    } finally {
                        permits.release();
                    }
                });
                tasks.add(new StudyTask(studyUid, files, future));
            }
            if (attempts == null) {
                attempts = joinInSubmissionOrder(tasks);
            }
        }

        List<IngestResponse.StudyResult> studyResults = new ArrayList<>();
        int totalAccepted = 0;
        int totalRejected = 0;

        for (StudyAttempt attempt : attempts) {
            String studyUid = attempt.studyInstanceUid();
            List<ValidatedDicom> studyFiles = validStudies.get(studyUid);
            int studyFileCount = studyFiles != null ? studyFiles.size() : 0;

            if (attempt.failure() != null || attempt.result() == null) {
                totalRejected += studyFileCount;
                studyResults.add(new IngestResponse.StudyResult(
                    studyUid,
                    IngestResponse.StudyStatus.FAILED,
                    List.of(),
                    "ARCHIVE_UNAVAILABLE"
                ));
            } else {
                StowStudyResult result = attempt.result();
                List<IngestResponse.InstanceResult> instanceResults = new ArrayList<>();
                int studyAccepted = 0;
                int studyRejected = 0;

                for (StowInstanceResult inst : result.instances()) {
                    instanceResults.add(new IngestResponse.InstanceResult(
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

                IngestResponse.StudyStatus status;
                if (instanceResults.isEmpty()) {
                    status = IngestResponse.StudyStatus.FAILED;
                } else if (studyAccepted == instanceResults.size()) {
                    status = IngestResponse.StudyStatus.COMPLETE;
                } else if (studyAccepted == 0) {
                    status = IngestResponse.StudyStatus.FAILED;
                } else {
                    status = IngestResponse.StudyStatus.PARTIAL;
                }

                studyResults.add(new IngestResponse.StudyResult(
                    studyUid,
                    status,
                    instanceResults,
                    null
                ));
            }
        }

        IngestResponse.Summary summary = new IngestResponse.Summary(
            received,
            locallyValid,
            locallyRejected,
            totalAccepted,
            totalRejected
        );

        IngestResponse.Outcome outcome;
        if (totalAccepted == received && locallyRejected == 0) {
            outcome = IngestResponse.Outcome.COMPLETE;
        } else if (totalAccepted == 0) {
            outcome = IngestResponse.Outcome.FAILED;
        } else {
            outcome = IngestResponse.Outcome.PARTIAL;
        }

        int suggestedStatus;
        if (received > 0 && locallyValid == 0) {
            suggestedStatus = 422;
        } else if (!attempts.isEmpty() && attempts.stream().allMatch(a -> a.failure() != null) && totalAccepted == 0) {
            suggestedStatus = 503;
        } else {
            suggestedStatus = 200;
        }

        IngestResponse response = new IngestResponse(outcome, summary, studyResults, locallyRejectedFiles);
        return new IngestExecution(suggestedStatus, response);
    }

    private StudyAttempt store(String studyUid, List<ValidatedDicom> files, String accessToken) {
        try {
            StowStudyResult result = gateway.storeStudy(studyUid, files, accessToken);
            return new StudyAttempt(studyUid, result, null);
        } catch (ArchiveUnavailableException e) {
            return new StudyAttempt(studyUid, null, e);
        } catch (Exception e) {
            return new StudyAttempt(
                studyUid,
                null,
                new ArchiveUnavailableException(ArchiveUnavailableException.Reason.CONNECTION, e)
            );
        }
    }

    private List<StudyAttempt> joinInSubmissionOrder(List<StudyTask> tasks) {
        List<StudyAttempt> attempts = new ArrayList<>(tasks.size());
        for (StudyTask task : tasks) {
            try {
                attempts.add(task.future().get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                attempts.add(new StudyAttempt(
                    task.studyInstanceUid(),
                    null,
                    new ArchiveUnavailableException(ArchiveUnavailableException.Reason.INTERRUPTED, e)
                ));
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                ArchiveUnavailableException ex = cause instanceof ArchiveUnavailableException aue
                    ? aue
                    : new ArchiveUnavailableException(ArchiveUnavailableException.Reason.CONNECTION, cause);
                attempts.add(new StudyAttempt(task.studyInstanceUid(), null, ex));
            }
        }
        return attempts;
    }

    private record StudyTask(String studyInstanceUid, List<ValidatedDicom> files, Future<StudyAttempt> future) {}

    private record StudyAttempt(
        String studyInstanceUid,
        StowStudyResult result,
        ArchiveUnavailableException failure
    ) {}
}
