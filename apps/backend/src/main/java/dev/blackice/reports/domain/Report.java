package dev.blackice.reports.domain;

import dev.blackice.reports.application.exception.InvalidReportRequestException;
import dev.blackice.reports.application.input.ReportActor;
import dev.blackice.reports.application.input.ReportContent;
import dev.blackice.reports.application.input.ReportStudyRef;

import java.time.Instant;

/**
 * Immutable domain record representing a clinical report attached to a DICOM study.
 * Implements strict lifecycle rules:
 * - DRAFT -> DRAFT (saved revision, version incremented)
 * - DRAFT -> FINAL (finalization, version incremented, finalizedAt recorded)
 * - FINAL -> terminal state (all mutations rejected)
 */
public record Report(
    String studyInstanceUid,
    String authorId,
    String authorDisplayName,
    ReportStatus status,
    String content,
    long version,
    Instant createdAt,
    Instant updatedAt,
    Instant finalizedAt
) {

    public Report {
        if (studyInstanceUid == null || authorId == null || authorDisplayName == null
                || status == null || content == null || createdAt == null || updatedAt == null) {
            throw new InvalidReportRequestException();
        }
        if (status == ReportStatus.FINAL && finalizedAt == null) {
            throw new InvalidReportRequestException();
        }
        if (status == ReportStatus.DRAFT && finalizedAt != null) {
            throw new InvalidReportRequestException();
        }
    }

    public static Report create(
        ReportStudyRef study,
        ReportActor actor,
        ReportContent content,
        ReportStatus status,
        Instant now
    ) {
        if (study == null || actor == null || content == null || status == null || now == null) {
            throw new InvalidReportRequestException();
        }
        Instant finalizedAt = (status == ReportStatus.FINAL) ? now : null;
        return new Report(
            study.studyInstanceUid(),
            actor.subject(),
            actor.displayName(),
            status,
            content.value(),
            0L,
            now,
            now,
            finalizedAt
        );
    }

    public Report revise(ReportContent content, ReportStatus targetStatus, Instant now) {
        if (content == null || targetStatus == null || now == null) {
            throw new InvalidReportRequestException();
        }
        if (this.status == ReportStatus.FINAL) {
            throw new InvalidReportRequestException("Report is in final state and cannot be modified");
        }
        Instant finalizedAt = (targetStatus == ReportStatus.FINAL) ? now : null;
        return new Report(
            this.studyInstanceUid,
            this.authorId,
            this.authorDisplayName,
            targetStatus,
            content.value(),
            this.version + 1L,
            this.createdAt,
            now,
            finalizedAt
        );
    }
}
