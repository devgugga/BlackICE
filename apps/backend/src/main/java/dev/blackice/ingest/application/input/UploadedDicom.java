package dev.blackice.ingest.application.input;

import java.nio.file.Path;

public record UploadedDicom(Path path, String filename, long size) {}
