package dev.blackice.reports.application.exception;

/**
 * Domain and application exception raised when report request parameters violate validation constraints.
 * Note: Never echo clinical identifiers, UIDs, or raw user inputs in exception messages.
 */
public final class InvalidReportRequestException extends RuntimeException {

    public InvalidReportRequestException() {
        super("INVALID_REPORT_REQUEST");
    }

    public InvalidReportRequestException(String message) {
        super(message);
    }

    public InvalidReportRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
