package dev.blackice.reports.application.exception;

/**
 * Application exception raised when an authenticated user attempts to mutate a report authored by someone else.
 */
public final class ReportAccessDeniedException extends RuntimeException {

    public ReportAccessDeniedException() {
        super("REPORT_ACCESS_DENIED");
    }

    public ReportAccessDeniedException(String message) {
        super(message);
    }

    public ReportAccessDeniedException(String message, Throwable cause) {
        super(message, cause);
    }
}
