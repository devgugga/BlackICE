package dev.blackice.viewer.application.exception;

import java.util.Objects;

/**
 * Safe domain exception raised when DICOMweb study hierarchy discovery cannot be performed, times out, or fails.
 *
 * <p>Security Invariant: Exception messages are constrained to the stable {@link Reason} name
 * to prevent leaking raw payloads, patient identifiers, or security tokens in stack traces or logs.</p>
 */
public final class ArchiveViewerException extends RuntimeException {

    /**
     * Categorized reason for the archive hierarchy discovery failure.
     */
    public enum Reason {
        /** Security token was rejected or unauthenticated (HTTP 401). */
        AUTHENTICATION,
        /** Security token lacks permission for the study (HTTP 403). */
        ACCESS_DENIED,
        /** Study or resource was not found in the archive (HTTP 404 or empty study query). */
        NOT_FOUND,
        /** Archive is temporarily unavailable or returned a 5xx server error. */
        UNAVAILABLE,
        /** Connection or request timed out. */
        TIMEOUT,
        /** Network connection could not be established or thread was interrupted. */
        CONNECTION,
        /** Archive returned invalid content-type, corrupt DICOM JSON, or inconsistent hierarchy data. */
        INVALID_RESPONSE
    }

    private final Reason reason;

    public ArchiveViewerException(Reason reason) {
        this(reason, null);
    }

    public ArchiveViewerException(Reason reason, Throwable cause) {
        super(reason != null ? reason.name() : null, cause);
        this.reason = Objects.requireNonNull(reason, "reason must not be null");
    }

    /**
     * Returns the structured reason for the failure.
     *
     * @return the failure reason enum
     */
    public Reason reason() {
        return reason;
    }
}
