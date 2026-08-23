package dev.blackice.ingest.application.result;

import java.util.List;

/**
 * Result of storing instances for a specific Study Instance UID in the DICOM archive.
 *
 * @param studyInstanceUid exact Study Instance UID of the stored study
 * @param instances list of individual SOP instance storage outcomes
 */
public record StowStudyResult(
    String studyInstanceUid,
    List<StowInstanceResult> instances
) {}
