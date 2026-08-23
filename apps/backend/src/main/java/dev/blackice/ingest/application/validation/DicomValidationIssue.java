package dev.blackice.ingest.application.validation;

/**
 * Describes a validation failure or anomaly identified in an uploaded DICOM file.
 *
 * <p>Security Invariant: {@code message} is a stable English constant derived
 * from {@link Code}. It never embeds a DICOM UID, a filename or a raw exception
 * message, because it is published in the public problem extension.
 *
 * @param itemIndex position of the file in the order the upload was received
 * @param filename original name of the rejected file, for the local result only
 * @param code stable classification code for the validation failure
 * @param message safe English message explaining the issue
 */
public record DicomValidationIssue(int itemIndex, String filename, Code code, String message) {

    /**
     * Stable error and rejection codes for DICOM validation.
     */
    public enum Code {
        /** The file could not be parsed as a valid DICOM Part 10 dataset. */
        MALFORMED_DICOM("The file is not valid DICOM."),
        /** Mandatory Study Instance UID (0020,000D) is absent or empty. */
        MISSING_STUDY_INSTANCE_UID("Required DICOM attribute Study Instance UID is missing."),
        /** Mandatory Series Instance UID (0020,000E) is absent or empty. */
        MISSING_SERIES_INSTANCE_UID("Required DICOM attribute Series Instance UID is missing."),
        /** Mandatory SOP Instance UID (0008,0018) is absent or empty. */
        MISSING_SOP_INSTANCE_UID("Required DICOM attribute SOP Instance UID is missing."),
        /** Mandatory SOP Class UID (0008,0016) is absent or empty. */
        MISSING_SOP_CLASS_UID("Required DICOM attribute SOP Class UID is missing."),
        /** Another identical file with the same SOP UID and byte hash was already retained in this batch. */
        DUPLICATE_IDENTICAL("An identical copy of this instance is already present in the batch."),
        /** Multiple distinct files in this batch share the same SOP UID with differing byte payloads. */
        SOP_UID_COLLISION("Another file in the batch declares the same SOP Instance UID with different content.");

        private final String message;

        Code(String message) {
            this.message = message;
        }

        /** Safe, stable English text published for this code. */
        public String message() {
            return message;
        }
    }

    /** Builds an issue whose message is the safe constant of its code. */
    public static DicomValidationIssue of(int itemIndex, String filename, Code code) {
        return new DicomValidationIssue(itemIndex, filename, code, code.message());
    }
}
