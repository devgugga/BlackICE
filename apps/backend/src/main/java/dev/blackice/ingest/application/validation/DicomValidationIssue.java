package dev.blackice.ingest.application.validation;

/**
 * Describes a validation failure or anomaly identified in an uploaded DICOM file.
 *
 * @param filename original name of the rejected file
 * @param code stable classification code for the validation failure
 * @param message descriptive English message explaining the issue
 */
public record DicomValidationIssue(String filename, Code code, String message) {

    /**
     * Stable error and rejection codes for DICOM validation.
     */
    public enum Code {
        /** The file could not be parsed as a valid DICOM Part 10 dataset. */
        MALFORMED_DICOM,
        /** Mandatory Study Instance UID (0020,000D) is absent or empty. */
        MISSING_STUDY_INSTANCE_UID,
        /** Mandatory Series Instance UID (0020,000E) is absent or empty. */
        MISSING_SERIES_INSTANCE_UID,
        /** Mandatory SOP Instance UID (0008,0018) is absent or empty. */
        MISSING_SOP_INSTANCE_UID,
        /** Mandatory SOP Class UID (0008,0016) is absent or empty. */
        MISSING_SOP_CLASS_UID,
        /** Another identical file with the same SOP UID and byte hash was already retained in this batch. */
        DUPLICATE_IDENTICAL,
        /** Multiple distinct files in this batch share the same SOP UID with differing byte payloads. */
        SOP_UID_COLLISION
    }
}
