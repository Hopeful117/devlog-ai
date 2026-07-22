package com.hopeful117.devlogai.collection.collector;

import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class CollectorRunner {
    private final CollectorLimits limits;

    public CollectorRunner(CollectorLimits limits) {
        this.limits = limits;
    }

    public CollectionResult run(KnowledgeCollector collector, CollectionContext context) {
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            return executor.submit(() -> collector.collect(context)).get(
                    limits.getCollectorTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            throw new NonFatalCollectionException(
                    "COLLECTOR_TIMEOUT", "Collector execution timeout reached");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Collection interrupted", exception);
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Collector execution failed", exception.getCause());
        } finally {
            executor.shutdownNow();
        }
    }
}
