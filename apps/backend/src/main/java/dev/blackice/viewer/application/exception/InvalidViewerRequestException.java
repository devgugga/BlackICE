package dev.blackice.viewer.application.exception;

/**
 * Thrown when a viewer request contains invalid or syntactically incorrect parameters.
 * Note: Never echo clinical identifiers, UIDs, or raw user inputs in exception messages.
 */
public final class InvalidViewerRequestException extends RuntimeException {

    public InvalidViewerRequestException() {
        super("INVALID_VIEWER_REQUEST");
    }

    public InvalidViewerRequestException(String message) {
        super(message);
    }
}
