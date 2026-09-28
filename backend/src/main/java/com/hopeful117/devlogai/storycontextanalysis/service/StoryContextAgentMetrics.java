package com.hopeful117.devlogai.storycontextanalysis.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Metrics contain only bounded operation/result labels, never request content. */
@Component
public class StoryContextAgentMetrics {
    private static final String[] COUNTERS = {
            "sca_protocol_requests_total", "sca_projection_construction_total",
            "sca_budget_truncated_total", "sca_digest_mismatch_total",
            "sca_grounding_rejection_total", "sca_idempotent_retry_total",
            "sca_duplicate_terminal_total"
    };

    private final MeterRegistry registry;
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    public StoryContextAgentMetrics(MeterRegistry registry) {
        this.registry = registry;
        for (String name : COUNTERS) counters.put(name, registry.counter(name));
    }

    public void increment(String name) {
        Counter counter = counters.get(name);
        if (counter != null) counter.increment();
    }

    public <T> T time(String operation, Supplier<T> action) {
        Timer.Sample sample = Timer.start(registry);
        try {
            return action.get();
        } finally {
            sample.stop(registry.timer("sca_operation_latency", "operation", operation));
        }
    }

    public void request(String operation) {
        increment("sca_protocol_requests_total");
    }
}
