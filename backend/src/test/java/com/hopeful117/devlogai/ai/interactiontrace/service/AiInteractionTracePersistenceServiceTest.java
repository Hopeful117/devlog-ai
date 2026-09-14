package com.hopeful117.devlogai.ai.interactiontrace.service;

import com.hopeful117.devlogai.ai.engine.dto.AiInteractionTraceRequest;
import com.hopeful117.devlogai.ai.interactiontrace.entity.AiInteractionTrace;
import com.hopeful117.devlogai.ai.interactiontrace.repository.AiInteractionTraceRepository;
import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.analysis.entity.Analysis;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiInteractionTracePersistenceServiceTest {
    @Mock
    private AiInteractionTraceRepository repository;

    @InjectMocks
    private AiInteractionTracePersistenceService service;

    @Test
    void normalModePersistsMetadataWithoutDiagnosticPayload() {
        ReflectionTestUtils.setField(service, "configuredLevel", "NORMAL");
        AiTask task = task();
        AiInteractionTraceRequest request = request();
        when(repository.existsById(request.id())).thenReturn(false);

        service.persist(task, List.of(request));

        ArgumentCaptor<AiInteractionTrace> captor = ArgumentCaptor.forClass(AiInteractionTrace.class);
        verify(repository).save(captor.capture());
        AiInteractionTrace trace = captor.getValue();
        assertThat(trace.getTraceLevel()).isEqualTo("NORMAL");
        assertThat(trace.getSystemPrompt()).isNull();
        assertThat(trace.getRawModelResponse()).isNull();
        assertThat(trace.getParsedModelResponse()).isNull();
        assertThat(trace.getExpiresAt()).isNull();
    }

    @Test
    void diagnosticModePersistsRichPayloadWithExpiry() {
        ReflectionTestUtils.setField(service, "configuredLevel", "DIAGNOSTIC");
        ReflectionTestUtils.setField(service, "diagnosticRetention", java.time.Duration.ofDays(7));
        AiTask task = task();
        AiInteractionTraceRequest request = request();
        when(repository.existsById(request.id())).thenReturn(false);

        service.persist(task, List.of(request));

        ArgumentCaptor<AiInteractionTrace> captor = ArgumentCaptor.forClass(AiInteractionTrace.class);
        verify(repository).save(captor.capture());
        AiInteractionTrace trace = captor.getValue();
        assertThat(trace.getTraceLevel()).isEqualTo("DIAGNOSTIC");
        assertThat(trace.getSystemPrompt()).isEqualTo("system");
        assertThat(trace.getRawModelResponse()).isEqualTo("{}");
        assertThat(trace.getParsedModelResponse()).isEqualTo(Map.of("ok", true));
        assertThat(trace.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void duplicateTraceIdIsIdempotent() {
        ReflectionTestUtils.setField(service, "configuredLevel", "NORMAL");
        AiInteractionTraceRequest request = request();
        when(repository.existsById(request.id())).thenReturn(true);

        service.persist(task(), List.of(request));

        verify(repository, never()).save(any());
    }

    private AiTask task() {
        Analysis analysis = new Analysis();
        analysis.setId(UUID.randomUUID());
        AiTask task = new AiTask();
        task.setId(UUID.randomUUID());
        task.setAnalysis(analysis);
        task.setCorrelationId(UUID.randomUUID());
        return task;
    }

    private AiInteractionTraceRequest request() {
        Instant started = Instant.now().minus(25, ChronoUnit.MILLIS);
        return new AiInteractionTraceRequest(
                UUID.randomUUID(), 1, "INITIAL_GENERATION", "DIAGNOSTIC", "mock",
                "deterministic-v1", "describe-project", "v1", "describe-project-prompt-v1",
                started, Instant.now(), 25, "SUCCEEDED", null, null,
                "a".repeat(64), "b".repeat(64), 1, 1, 1, 0, "c".repeat(64),
                null, null, null, null, null, "system", "user", "{}", Map.of("ok", true), null);
    }
}
