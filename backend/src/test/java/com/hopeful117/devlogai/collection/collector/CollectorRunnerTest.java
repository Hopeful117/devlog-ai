package com.hopeful117.devlogai.collection.collector;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
    void shouldThrowNonFatalCollectionExceptionOnTimeout() throws InterruptedException {
        CollectorLimits limits = mock(CollectorLimits.class);
        when(limits.getCollectorTimeout()).thenReturn(Duration.ofMillis(100));

        CollectorRunner runner = new CollectorRunner(limits);
        KnowledgeCollector collector = mock(KnowledgeCollector.class);
        CollectionContext context = mock(CollectionContext.class);
        CountDownLatch collectorStarted = new CountDownLatch(1);
        CountDownLatch collectorInterrupted = new CountDownLatch(1);

        when(collector.collect(context)).thenAnswer(invocation -> {
            collectorStarted.countDown();
            try {
                new CountDownLatch(1).await();
                return mock(CollectionResult.class);
            } catch (InterruptedException exception) {
                collectorInterrupted.countDown();
                Thread.currentThread().interrupt();
                return mock(CollectionResult.class);
            }
        });

        NonFatalCollectionException exception = assertThrows(
                NonFatalCollectionException.class, () -> runner.run(collector, context));

        assertEquals("COLLECTOR_TIMEOUT", exception.code());
        assertTrue(collectorStarted.await(1, TimeUnit.SECONDS));
        assertTrue(collectorInterrupted.await(1, TimeUnit.SECONDS));
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

    @Test
    void shouldWrapNonRuntimeFailureFromCollector() {
        CollectorLimits limits = mock(CollectorLimits.class);
        when(limits.getCollectorTimeout()).thenReturn(Duration.ofSeconds(10));
        CollectorRunner runner = new CollectorRunner(limits);
        KnowledgeCollector collector = mock(KnowledgeCollector.class);
        CollectionContext context = mock(CollectionContext.class);
        AssertionError failure = new AssertionError("collector invariant");
        when(collector.collect(context)).thenThrow(failure);

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class, () -> runner.run(collector, context));

        assertSame(failure, thrown.getCause());
    }

    @Test
    void shouldRestoreCallerInterruptStatus() throws InterruptedException {
        CollectorLimits limits = mock(CollectorLimits.class);
        when(limits.getCollectorTimeout()).thenReturn(Duration.ofSeconds(10));
        CollectorRunner runner = new CollectorRunner(limits);
        KnowledgeCollector collector = mock(KnowledgeCollector.class);
        CollectionContext context = mock(CollectionContext.class);
        CountDownLatch collectorStarted = new CountDownLatch(1);
        when(collector.collect(context)).thenAnswer(invocation -> {
            collectorStarted.countDown();
            new CountDownLatch(1).await();
            return mock(CollectionResult.class);
        });
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<Boolean> interrupted = new AtomicReference<>(false);
        Thread caller = Thread.startVirtualThread(() -> {
            try {
                runner.run(collector, context);
            } catch (Throwable exception) {
                failure.set(exception);
                interrupted.set(Thread.currentThread().isInterrupted());
            }
        });
        assertTrue(collectorStarted.await(1, TimeUnit.SECONDS));

        caller.interrupt();
        caller.join(Duration.ofSeconds(1));

        assertInstanceOf(IllegalStateException.class, failure.get());
        assertTrue(interrupted.get());
        assertFalse(caller.isAlive());
    }
}
