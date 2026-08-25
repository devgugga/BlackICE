package dev.blackice.reports.application.exception;

/**
 * Application exception raised when a DICOM study does not exist in the external archive.
 */
public final class StudyNotFoundException extends RuntimeException {

    public StudyNotFoundException() {
        super("STUDY_NOT_FOUND");
    }

    public StudyNotFoundException(String message) {
        super(message);
    }

    public StudyNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
