package dev.blackice.reports.api;

/**
 * Incoming request body for creating or updating a study report.
 */
public record ReportRequest(String content, String status) {}
