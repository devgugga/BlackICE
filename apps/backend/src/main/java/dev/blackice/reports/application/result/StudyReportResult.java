package dev.blackice.reports.application.result;

import dev.blackice.reports.application.exception.InvalidReportRequestException;
import dev.blackice.reports.application.input.ReportActor;
import dev.blackice.reports.domain.Report;
import dev.blackice.reports.domain.ReportStatus;

import java.time.Instant;

/**
 * Public transport-independent projection of a study report.
 * Does not expose internal surrogate identifiers or author subject ID.
 */
public record StudyReportResult(
    String studyInstanceUid,
    String authorDisplayName,
    ReportStatus status,
    String content,
    boolean editable,
    Instant createdAt,
    Instant updatedAt,
    Instant finalizedAt,
    long version
) {

    public StudyReportResult {
        if (studyInstanceUid == null || authorDisplayName == null || status == null
                || content == null || createdAt == null || updatedAt == null || version < 0) {
            throw new InvalidReportRequestException();
        }
        if (status == ReportStatus.FINAL && finalizedAt == null) {
            throw new InvalidReportRequestException();
        }
        if (status == ReportStatus.DRAFT && finalizedAt != null) {
            throw new InvalidReportRequestException();
        }
    }

    public static StudyReportResult from(Report report, ReportActor currentActor) {
        if (report == null || currentActor == null) {
            throw new InvalidReportRequestException();
        }
        boolean editable = report.status() == ReportStatus.DRAFT
                && report.authorId().equals(currentActor.subject());
        return new StudyReportResult(
            report.studyInstanceUid(),
            report.authorDisplayName(),
            report.status(),
            report.content(),
            editable,
            report.createdAt(),
            report.updatedAt(),
            report.finalizedAt(),
            report.version()
        );
    }
}
