package dev.blackice.reports.application.exception;

/**
 * Application exception raised when an update's expected version does not match the current report version
 * or when an atomic conditional update fails due to concurrent modification.
 */
public final class ReportVersionConflictException extends RuntimeException {

    public ReportVersionConflictException() {
        super("REPORT_VERSION_CONFLICT");
    }

    public ReportVersionConflictException(String message) {
        super(message);
    }

    public ReportVersionConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
