package dev.blackice.viewer.application.exception;

/**
 * Thrown when metadata returned by the DICOM archive is corrupt, malformed,
 * contradictory, or missing required attributes.
 * Note: Never echo clinical identifiers, UIDs, or internal URLs in exception messages.
 */
public final class InvalidArchiveMetadataException extends RuntimeException {

    public InvalidArchiveMetadataException() {
        super("INVALID_ARCHIVE_METADATA");
    }

    public InvalidArchiveMetadataException(String message) {
        super(message);
    }

    public InvalidArchiveMetadataException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidArchiveMetadataException(Throwable cause) {
        super("INVALID_ARCHIVE_METADATA", cause);
    }
}
