package dev.blackice.reports.application.exception;

/**
 * Application exception raised when report content exceeds the maximum allowed payload size.
 */
public final class ReportPayloadTooLargeException extends RuntimeException {

    public ReportPayloadTooLargeException() {
        super("REPORT_PAYLOAD_TOO_LARGE");
    }

    public ReportPayloadTooLargeException(String message) {
        super(message);
    }

    public ReportPayloadTooLargeException(String message, Throwable cause) {
        super(message, cause);
    }
}
