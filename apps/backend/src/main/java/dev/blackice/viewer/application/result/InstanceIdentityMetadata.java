package dev.blackice.viewer.application.result;

/**
 * Lightweight instance identification metadata retrieved from QIDO for series classification.
 */
public record InstanceIdentityMetadata(
    String seriesInstanceUid,
    String sopInstanceUid,
    String sopClassUid,
    Integer numberOfFrames
) {}
