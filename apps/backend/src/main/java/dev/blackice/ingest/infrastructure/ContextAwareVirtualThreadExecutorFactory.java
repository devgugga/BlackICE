package dev.blackice.ingest.infrastructure;

import dev.blackice.ingest.application.port.TaskExecutorFactory;
import io.opentelemetry.context.Context;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Creates virtual-thread executors that capture the current OpenTelemetry context
 * when each task is submitted and restore it only while that task runs.
 */
@ApplicationScoped
public class ContextAwareVirtualThreadExecutorFactory implements TaskExecutorFactory {

    @Override
    public ExecutorService create() {
        return Context.taskWrapping(Executors.newVirtualThreadPerTaskExecutor());
    }
}
