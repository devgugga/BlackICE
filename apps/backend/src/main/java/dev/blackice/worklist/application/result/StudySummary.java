package dev.blackice.worklist.application.result;

import java.util.List;

/**
 * Immutable summary representation of a DICOM study in search results.
 */
public record StudySummary(
    String studyInstanceUid,
    String patientName,
    String patientId,
    String patientIdIssuer,
    String studyDate,
    String studyTime,
    List<String> modalities,
    String description,
    Integer seriesCount,
    Integer instanceCount
) {
    public StudySummary {
        modalities = modalities == null ? List.of() : List.copyOf(modalities);
    }
}
