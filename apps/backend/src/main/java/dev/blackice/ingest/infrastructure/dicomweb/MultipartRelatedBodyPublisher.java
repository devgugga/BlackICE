package dev.blackice.ingest.infrastructure.dicomweb;

import dev.blackice.ingest.application.validation.ValidatedDicom;

import java.io.FileNotFoundException;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Utility that formats an ordered list of DICOM instances into a standard streaming
 * {@code multipart/related; type="application/dicom"} request body.
 *
 * <p>Memory Invariant: Uses {@link HttpRequest.BodyPublishers#ofFile(java.nio.file.Path)} to stream
 * file parts directly from disk, preventing large DICOM pixel payloads from buffering into the JVM heap.</p>
 */
public final class MultipartRelatedBodyPublisher {

    private MultipartRelatedBodyPublisher() {}

    /**
     * Constructs a composite {@link HttpRequest.BodyPublisher} formatted with DICOMweb multipart boundaries.
     *
     * @param files validated DICOM files to stream
     * @param boundary MIME boundary delimiter string
     * @return a streaming body publisher for the HTTP client
     * @throws FileNotFoundException if any instance file is missing on disk
     */
    public static HttpRequest.BodyPublisher publish(
        List<ValidatedDicom> files,
        String boundary
    ) throws FileNotFoundException {
        Objects.requireNonNull(files, "files must not be null");
        Objects.requireNonNull(boundary, "boundary must not be null");

        List<HttpRequest.BodyPublisher> parts = new ArrayList<>();
        for (ValidatedDicom file : files) {
            parts.add(HttpRequest.BodyPublishers.ofByteArray((
                "--" + boundary + "\r\n" +
                "Content-Type: application/dicom\r\n\r\n"
            ).getBytes(StandardCharsets.US_ASCII)));
            parts.add(HttpRequest.BodyPublishers.ofFile(file.path()));
            parts.add(HttpRequest.BodyPublishers.ofByteArray("\r\n".getBytes(StandardCharsets.US_ASCII)));
        }
        parts.add(HttpRequest.BodyPublishers.ofByteArray((
            "--" + boundary + "--\r\n"
        ).getBytes(StandardCharsets.US_ASCII)));
        return HttpRequest.BodyPublishers.concat(parts.toArray(HttpRequest.BodyPublisher[]::new));
    }
}
