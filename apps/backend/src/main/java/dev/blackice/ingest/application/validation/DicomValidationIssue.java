package dev.blackice.ingest.application.validation;

public record DicomValidationIssue(String filename, Code code, String message) {
    public enum Code {
        MALFORMED_DICOM,
        MISSING_STUDY_INSTANCE_UID,
        MISSING_SERIES_INSTANCE_UID,
        MISSING_SOP_INSTANCE_UID,
        MISSING_SOP_CLASS_UID,
        DUPLICATE_IDENTICAL,
        SOP_UID_COLLISION
    }
}
