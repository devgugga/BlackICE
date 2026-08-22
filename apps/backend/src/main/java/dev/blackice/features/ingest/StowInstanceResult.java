package dev.blackice.features.ingest;

public record StowInstanceResult(
    String sopInstanceUid,
    Status status,
    Integer reason
) {
    public enum Status { ACCEPTED, WARNING, REJECTED, UNCONFIRMED }
}
