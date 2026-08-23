package dev.blackice.worklist.application.exception;

/**
 * Domain exception raised when study search query parameters violate validation constraints.
 */
public final class InvalidStudySearchException extends RuntimeException {

    public InvalidStudySearchException(String message) {
        super(message);
    }

    public InvalidStudySearchException(String message, Throwable cause) {
        super(message, cause);
    }
}
