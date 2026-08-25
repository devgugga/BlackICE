package dev.blackice.viewer.application.result;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Encapsulates an open DICOMweb multipart frame response stream and its Content-Type header.
 *
 * <p>Implements {@link AutoCloseable} to guarantee the underlying HTTP response body
 * input stream is closed after frame transfer.</p>
 */
public record FrameStream(String contentType, InputStream body) implements AutoCloseable {

    public FrameStream {
        Objects.requireNonNull(contentType, "contentType must not be null");
        Objects.requireNonNull(body, "body must not be null");
    }

    @Override
    public void close() throws IOException {
        if (body != null) {
            body.close();
        }
    }
}
