package dev.blackice.ingest.infrastructure.dicomweb;

import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

/** Conservatively records when the HTTP client begins consuming a request body. */
final class SubmissionTrackingBodyPublisher implements HttpRequest.BodyPublisher {

    private final HttpRequest.BodyPublisher delegate;
    private final AtomicBoolean submissionStarted = new AtomicBoolean();

    SubmissionTrackingBodyPublisher(HttpRequest.BodyPublisher delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    @Override
    public long contentLength() {
        return delegate.contentLength();
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        submissionStarted.set(true);
        delegate.subscribe(subscriber);
    }

    boolean submissionStarted() {
        return submissionStarted.get();
    }
}
