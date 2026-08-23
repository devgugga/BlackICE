package dev.blackice.ingest.application.input;

import java.nio.file.Path;

/**
 * Immutable input representing an unvalidated uploaded DICOM file spooled to local storage.
 *
 * @param path filesystem path where the multipart part was temporarily written
 * @param filename original filename reported by the client
 * @param size size of the file in bytes
 */
public record UploadedDicom(Path path, String filename, long size) {}
