package dev.blackice.viewer.application.result;

import java.util.List;

/**
 * Curated top-level study viewer payload containing study metadata and classified series.
 */
public record StudyViewerSummary(
    String studyInstanceUid,
    String patientName,
    String patientId,
    String patientIdIssuer,
    String studyDate,
    String studyTime,
    String description,
    List<ViewerSeriesSummary> series
) {
    public StudyViewerSummary {
        series = series == null ? List.of() : List.copyOf(series);
    }
}
