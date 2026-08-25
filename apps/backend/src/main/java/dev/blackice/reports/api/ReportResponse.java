package dev.blackice.reports.api;

import dev.blackice.reports.domain.ReportStatus;

import java.time.Instant;

/**
 * Public JSON representation of a study report.
 * Exactly 8 public fields; excludes internal database ID, authorId, and raw version.
 */
public record ReportResponse(
    String studyInstanceUid,
    String authorDisplayName,
    ReportStatus status,
    String content,
    boolean editable,
    Instant createdAt,
    Instant updatedAt,
    Instant finalizedAt
) {}
