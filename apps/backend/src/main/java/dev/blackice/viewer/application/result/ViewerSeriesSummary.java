package dev.blackice.viewer.application.result;

/**
 * Curated summary of a DICOM series with support classification for the viewer.
 */
public record ViewerSeriesSummary(
    String seriesInstanceUid,
    Integer seriesNumber,
    String modality,
    String description,
    Integer instanceCount,
    SeriesAvailability availability,
    UnsupportedReason unsupportedReason
) {}
