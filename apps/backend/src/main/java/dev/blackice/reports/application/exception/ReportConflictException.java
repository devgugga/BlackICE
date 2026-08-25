package dev.blackice.reports.application.exception;

/**
 * Application exception raised when a report operation conflicts with current state,
 * such as an existing report during creation or attempting to modify a FINAL report.
 */
public final class ReportConflictException extends RuntimeException {

    public ReportConflictException() {
        super("REPORT_CONFLICT");
    }

    public ReportConflictException(String message) {
        super(message);
    }

    public ReportConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
