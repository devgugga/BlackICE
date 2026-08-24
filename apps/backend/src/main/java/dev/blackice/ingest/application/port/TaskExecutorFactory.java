package dev.blackice.ingest.application.port;

import java.util.concurrent.ExecutorService;

/**
 * Creates the executor used to submit independent study storage tasks.
 *
 * <p>Infrastructure adapters may preserve request-scoped context without exposing
 * telemetry APIs to the application layer.</p>
 */
@FunctionalInterface
public interface TaskExecutorFactory {

    ExecutorService create();
}
