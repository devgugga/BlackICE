package dev.blackice.reports.application.exception;

/**
 * Application exception raised when attempting to update a study report that does not exist in the database.
 */
public final class ReportNotFoundException extends RuntimeException {

    public ReportNotFoundException() {
        super("REPORT_NOT_FOUND");
    }

    public ReportNotFoundException(String message) {
        super(message);
    }

    public ReportNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
