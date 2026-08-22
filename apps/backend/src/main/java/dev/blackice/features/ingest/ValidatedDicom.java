package dev.blackice.features.ingest;

import java.nio.file.Path;

public record ValidatedDicom(
    Path path,
    String filename,
    long size,
    String studyInstanceUid,
    String seriesInstanceUid,
    String sopInstanceUid,
    String sopClassUid,
    String sha256
) {}
