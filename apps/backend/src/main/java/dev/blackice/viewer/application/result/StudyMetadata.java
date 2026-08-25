package dev.blackice.viewer.application.result;

/**
 * Metadata for a DICOM study retrieved from the archive hierarchy.
 */
public record StudyMetadata(
    String studyInstanceUid,
    String patientName,
    String patientId,
    String patientIdIssuer,
    String studyDate,
    String studyTime,
    String description
) {}
