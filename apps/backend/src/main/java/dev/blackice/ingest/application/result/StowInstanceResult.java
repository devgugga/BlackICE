package dev.blackice.ingest.application.result;

public record StowInstanceResult(
    String sopInstanceUid,
    Status status,
    Integer reason
) {
    public enum Status { ACCEPTED, WARNING, REJECTED, UNCONFIRMED }
}
