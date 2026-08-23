package dev.blackice.ingest.application.validation;

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
