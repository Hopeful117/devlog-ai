package com.hopeful117.devlogai.collection.collector;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CollectorRunnerTest {

    @Test
    void shouldRunCollectorSuccessfully() {
        CollectorLimits limits = mock(CollectorLimits.class);
        when(limits.getCollectorTimeout()).thenReturn(Duration.ofSeconds(30));

        CollectorRunner runner = new CollectorRunner(limits);
        KnowledgeCollector collector = mock(KnowledgeCollector.class);
        CollectionContext context = mock(CollectionContext.class);
        CollectionResult expectedResult = mock(CollectionResult.class);

        when(collector.collect(context)).thenReturn(expectedResult);

        CollectionResult result = runner.run(collector, context);

        assertSame(expectedResult, result);
        verify(collector).collect(context);
    }

    @Test
    void shouldThrowNonFatalCollectionExceptionOnTimeout() {
        CollectorLimits limits = mock(CollectorLimits.class);
        when(limits.getCollectorTimeout()).thenReturn(Duration.ofMillis(1));

        CollectorRunner runner = new CollectorRunner(limits);
        KnowledgeCollector collector = mock(KnowledgeCollector.class);
        CollectionContext context = mock(CollectionContext.class);

        when(collector.collect(context)).thenAnswer(invocation -> {
            Thread.sleep(100);
            return mock(CollectionResult.class);
        });

        assertThrows(NonFatalCollectionException.class,
                () -> runner.run(collector, context));
    }

    @Test
    void shouldReThrowRuntimeExceptionFromCollector() {
        CollectorLimits limits = mock(CollectorLimits.class);
        when(limits.getCollectorTimeout()).thenReturn(Duration.ofSeconds(10));

        CollectorRunner runner = new CollectorRunner(limits);
        KnowledgeCollector collector = mock(KnowledgeCollector.class);
        CollectionContext context = mock(CollectionContext.class);

        RuntimeException testException = new RuntimeException("Test failure");
        when(collector.collect(context)).thenThrow(testException);

        // CollectorRunner re-throws RuntimeException directly (not wrapped in IllegalStateException)
        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> runner.run(collector, context));
        assertSame(testException, thrown);
    }
}
