package dev.blackice.features.ingest;

import java.nio.file.Path;

public record UploadedDicom(Path path, String filename, long size) {}
