package dev.blackice.ingest.application.validation;

import java.nio.file.Path;

/**
 * Immutable representation of a validated DICOM instance whose required UIDs have been safely extracted.
 *
 * <p>DICOM Invariant: Extracted UIDs must be preserved as exact strings without mutation or generation.</p>
 *
 * @param path filesystem path to the raw DICOM file
 * @param filename client-provided filename for logging and reporting
 * @param size byte size of the file
 * @param studyInstanceUid exact Study Instance UID (0020,000D)
 * @param seriesInstanceUid exact Series Instance UID (0020,000E)
 * @param sopInstanceUid exact SOP Instance UID (0008,0018)
 * @param sopClassUid exact SOP Class UID (0008,0016)
 * @param sha256 hex-encoded SHA-256 hash of the file bytes for duplicate/collision detection
 */
public record ValidatedDicom(
    Path path,
    String filename,
    long size,
    String studyInstanceUid,
    String seriesInstanceUid,
    String sopInstanceUid,
    String sopClassUid,
    String sha256
) {}
