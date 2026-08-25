package dev.blackice.reports.api;

import dev.blackice.reports.domain.ReportStatus;

/**
 * Incoming request body for creating or updating a study report.
 */
public record ReportRequest(String content, ReportStatus status) {}
