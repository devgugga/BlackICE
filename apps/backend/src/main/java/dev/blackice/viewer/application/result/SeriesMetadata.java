package dev.blackice.viewer.application.result;

/**
 * Metadata for a DICOM series retrieved from the archive hierarchy.
 */
public record SeriesMetadata(
    String seriesInstanceUid,
    Integer seriesNumber,
    String modality,
    String description,
    Integer instanceCount
) {}
