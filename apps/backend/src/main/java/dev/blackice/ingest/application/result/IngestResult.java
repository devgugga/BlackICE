package dev.blackice.ingest.application.result;

import dev.blackice.ingest.application.validation.DicomValidationIssue;

import java.util.List;

/** Outcome produced by the ingest use case, independent of the HTTP transport. */
public record IngestResult(
    Outcome outcome,
    Summary summary,
    List<StudyResult> studies,
    List<RejectedFile> locallyRejectedFiles
) {
    public enum Outcome { COMPLETE, PARTIAL, FAILED }

    public record Summary(
        int received,
        int locallyValid,
        int locallyRejected,
        int archiveAccepted,
        int archiveRejected
    ) {}

    public record StudyResult(
        String studyInstanceUid,
        StudyStatus status,
        List<InstanceResult> instances,
        String errorCode
    ) {}

    public enum StudyStatus { COMPLETE, PARTIAL, FAILED }

    public record InstanceResult(
        String sopInstanceUid,
        StowInstanceResult.Status status,
        Integer reason
    ) {}

    /**
     * A locally rejected file.
     *
     * <p>{@code filename} exists only in the 200 result, which the uploader
     * already correlates with its own selection; it is never published in the
     * problem extension, where {@code itemIndex} identifies the item instead.
     */
    public record RejectedFile(
        int itemIndex,
        String filename,
        DicomValidationIssue.Code code,
        String message
    ) {}
}
