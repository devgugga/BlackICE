package dev.blackice.ingest.application.exception;

public final class ArchiveUnavailableException extends RuntimeException {

    public enum Reason { TIMEOUT, CONNECTION, HTTP_STATUS, INTERRUPTED }

    private final Reason reason;

    public ArchiveUnavailableException(Reason reason, Throwable cause) {
        super(reason.name(), cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
