package dev.blackice.reports.domain;

/**
 * Lifecycle status of a clinical report.
 * DRAFT: mutable draft report in progress.
 * FINAL: approved and finalized report; terminal immutable state.
 */
public enum ReportStatus {
    DRAFT,
    FINAL
}
