package dev.blackice.ingest.application.exception;

/**
 * Safe domain exception raised when the external DICOM archive cannot be reached or responds unexpectedly.
 *
 * <p>Security Invariant: Exception messages are constrained to the safe {@link Reason} name
 * to prevent leaking raw payloads, patient identifiers, or backend URLs in stack traces.</p>
 */
public final class ArchiveUnavailableException extends RuntimeException {

    /**
     * Categorized reason for the archive unavailability.
     */
    public enum Reason {
        /** The HTTP connection or request timed out. */
        TIMEOUT,
        /** Network connection to the archive could not be established. */
        CONNECTION,
        /** Archive returned an unexpected HTTP error status (5xx/4xx). */
        HTTP_STATUS,
        /**
         * The archive answered 2xx but the body could not be interpreted.
         *
         * <p>Distinct from {@link #TIMEOUT} and {@link #CONNECTION}: the request
         * did reach the archive, which may already have stored the instances.
         * Reporting it as unavailability would invite a duplicate re-upload.
         */
        INVALID_RESPONSE,
        /** The execution thread was interrupted while waiting for the archive. */
        INTERRUPTED
    }

    private final Reason reason;

    public ArchiveUnavailableException(Reason reason, Throwable cause) {
        super(reason.name(), cause);
        this.reason = reason;
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
