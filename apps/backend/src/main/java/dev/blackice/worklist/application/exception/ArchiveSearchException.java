package dev.blackice.worklist.application.exception;

import java.util.Objects;

/**
 * Safe domain exception raised when the external DICOM archive search cannot be performed, times out, or fails.
 *
 * <p>Security Invariant: Exception messages are constrained to the stable {@link Reason} name
 * to prevent leaking raw payloads, patient identifiers, or security tokens in stack traces or logs.</p>
 */
public final class ArchiveSearchException extends RuntimeException {

    /**
     * Categorized reason for the archive search failure.
     */
    public enum Reason {
        /** The query returned 413 Payload Too Large from the archive. */
        QUERY_TOO_BROAD,
        /** The archive returned an invalid response body or unrecognized content type. */
        INVALID_RESPONSE,
        /** The HTTP connection or request timed out. */
        TIMEOUT,
        /** Network connection to the archive could not be established or execution was interrupted. */
        CONNECTION,
        /** Archive returned an unexpected HTTP error status (other non-2xx). */
        HTTP_STATUS
    }

    private final Reason reason;

    public ArchiveSearchException(Reason reason, Throwable cause) {
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
