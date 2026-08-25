package dev.blackice.reports.application.exception;

import java.util.Objects;

/**
 * Safe domain exception raised when external DICOM archive study existence lookup fails.
 *
 * <p>Security Invariant: Exception messages are constrained to the stable {@link Reason} name
 * to prevent leaking raw payloads, patient identifiers, or security tokens in stack traces or logs.</p>
 */
public final class ArchiveStudyLookupException extends RuntimeException {

    /**
     * Categorized reason for the archive study existence lookup failure.
     */
    public enum Reason {
        /** Authentication with the archive failed or was rejected (HTTP 401/403). */
        ARCHIVE_AUTH_FAILED,
        /** Archive is unavailable, timed out, or connection failed (HTTP 5xx, timeout, connect error). */
        ARCHIVE_UNAVAILABLE,
        /** Archive returned an invalid response, unexpected status, or malformed DICOM JSON. */
        ARCHIVE_INVALID_RESPONSE
    }

    private final Reason reason;

    public ArchiveStudyLookupException(Reason reason) {
        this(reason, null);
    }

    public ArchiveStudyLookupException(Reason reason, Throwable cause) {
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
