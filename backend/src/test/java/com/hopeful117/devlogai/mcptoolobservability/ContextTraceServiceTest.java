package com.hopeful117.devlogai.mcptoolobservability;

import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import com.hopeful117.devlogai.ai.task.repository.AiTaskRepository;
import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ContextTraceServiceTest {

    @Test
    void getContextTrace_populatesToolNameFromDirectLookup() {
        UUID projectId = UUID.randomUUID();
        UUID aiTaskId = UUID.randomUUID();
        McpToolEventRepository repo = mock(McpToolEventRepository.class);
        AiTaskRepository taskRepo = mock(AiTaskRepository.class);

        when(repo.findCorrelationId(projectId, "inst-1", "op-1")).thenReturn(aiTaskId);
        AiTask task = minimalTask(aiTaskId, projectId);
        when(taskRepo.findById(aiTaskId)).thenReturn(Optional.of(task));

        McpToolEventRepository.SingleOperationProjection op = mock(McpToolEventRepository.SingleOperationProjection.class);
        when(op.getToolName()).thenReturn("devlog_story_agent");
        when(op.getStatus()).thenReturn("RESPONDED");
        when(op.getStartedAt()).thenReturn(Instant.parse("2026-01-01T10:00:00Z"));
        when(op.getCompletedAt()).thenReturn(Instant.parse("2026-01-01T10:00:03Z"));
        when(op.getDurationMs()).thenReturn(3000);
        when(op.getReasonCode()).thenReturn(null);
        when(repo.findSingleOperation(projectId, "inst-1", "op-1")).thenReturn(op);

        ContextTraceService service = new ContextTraceService(repo, taskRepo);
        ContextTraceView view = service.getContextTrace(projectId, "inst-1", "op-1");

        assertThat(view.toolName()).isEqualTo("devlog_story_agent");
        assertThat(view.status()).isEqualTo("RESPONDED");
        assertThat(view.durationMs()).isEqualTo(3000);
        assertThat(view.aiTaskId()).isEqualTo(aiTaskId);
    }

    @Test
    void getContextTrace_fallsBackToAiTaskStatusWhenNoOperationDetail() {
        UUID projectId = UUID.randomUUID();
        UUID aiTaskId = UUID.randomUUID();
        McpToolEventRepository repo = mock(McpToolEventRepository.class);
        AiTaskRepository taskRepo = mock(AiTaskRepository.class);

        when(repo.findCorrelationId(projectId, "inst-1", "op-1")).thenReturn(aiTaskId);
        AiTask task = minimalTask(aiTaskId, projectId);
        when(task.getStatus()).thenReturn(AiTaskStatus.COMPLETED);
        when(taskRepo.findById(aiTaskId)).thenReturn(Optional.of(task));
        when(repo.findSingleOperation(projectId, "inst-1", "op-1")).thenReturn(null);

        ContextTraceService service = new ContextTraceService(repo, taskRepo);
        ContextTraceView view = service.getContextTrace(projectId, "inst-1", "op-1");

        assertThat(view.toolName()).isNull();
        assertThat(view.status()).isEqualTo("COMPLETED");
        assertThat(view.durationMs()).isNull();
    }

    @Test
    void getContextTrace_extractsProjectSlugFromContextSnapshot() {
        UUID projectId = UUID.randomUUID();
        UUID aiTaskId = UUID.randomUUID();
        McpToolEventRepository repo = mock(McpToolEventRepository.class);
        AiTaskRepository taskRepo = mock(AiTaskRepository.class);

        when(repo.findCorrelationId(projectId, "inst-1", "op-1")).thenReturn(aiTaskId);
        AiTask task = minimalTask(aiTaskId, projectId);
        when(task.getContextSnapshot()).thenReturn(Map.of(
                "scope", Map.of("projectSlug", "devlog-ai", "intent", "investigate story")));
        when(taskRepo.findById(aiTaskId)).thenReturn(Optional.of(task));
        when(repo.findSingleOperation(projectId, "inst-1", "op-1")).thenReturn(null);

        ContextTraceService service = new ContextTraceService(repo, taskRepo);
        ContextTraceView view = service.getContextTrace(projectId, "inst-1", "op-1");

        assertThat(view.projectSlug()).isEqualTo("devlog-ai");
        assertThat(view.intent()).isEqualTo("investigate story");
        assertThat(view.contextAvailable()).isTrue();
    }

    @Test
    void getContextTrace_contextAvailableFalseWhenSnapshotAbsent() {
        UUID projectId = UUID.randomUUID();
        UUID aiTaskId = UUID.randomUUID();
        McpToolEventRepository repo = mock(McpToolEventRepository.class);
        AiTaskRepository taskRepo = mock(AiTaskRepository.class);

        when(repo.findCorrelationId(projectId, "inst-1", "op-1")).thenReturn(aiTaskId);
        AiTask task = minimalTask(aiTaskId, projectId);
        when(task.getContextSnapshot()).thenReturn(null);
        when(taskRepo.findById(aiTaskId)).thenReturn(Optional.of(task));
        when(repo.findSingleOperation(projectId, "inst-1", "op-1")).thenReturn(null);

        ContextTraceService service = new ContextTraceService(repo, taskRepo);
        ContextTraceView view = service.getContextTrace(projectId, "inst-1", "op-1");

        assertThat(view.contextAvailable()).isFalse();
        assertThat(view.sources()).isEmpty();
    }

    @Test
    void getContextTrace_throwsEntityNotFoundWhenNoCorrelation() {
        UUID projectId = UUID.randomUUID();
        McpToolEventRepository repo = mock(McpToolEventRepository.class);
        AiTaskRepository taskRepo = mock(AiTaskRepository.class);

        when(repo.findCorrelationId(projectId, "inst-1", "op-1")).thenReturn(null);

        ContextTraceService service = new ContextTraceService(repo, taskRepo);

        assertThatThrownBy(() -> service.getContextTrace(projectId, "inst-1", "op-1"))
                .isInstanceOf(EntityNotFoundException.class);
        verify(taskRepo, never()).findById(any());
    }

    @Test
    void getContextTrace_rejectsIfTaskProjectMismatch() {
        UUID projectId = UUID.randomUUID();
        UUID otherProjectId = UUID.randomUUID();
        UUID aiTaskId = UUID.randomUUID();
        McpToolEventRepository repo = mock(McpToolEventRepository.class);
        AiTaskRepository taskRepo = mock(AiTaskRepository.class);

        when(repo.findCorrelationId(projectId, "inst-1", "op-1")).thenReturn(aiTaskId);
        AiTask task = minimalTask(aiTaskId, otherProjectId);
        when(taskRepo.findById(aiTaskId)).thenReturn(Optional.of(task));

        ContextTraceService service = new ContextTraceService(repo, taskRepo);

        assertThatThrownBy(() -> service.getContextTrace(projectId, "inst-1", "op-1"))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getContextTraceByAiTaskId_delegatesToGetContextTrace() {
        UUID projectId = UUID.randomUUID();
        UUID aiTaskId = UUID.randomUUID();
        McpToolEventRepository repo = mock(McpToolEventRepository.class);
        AiTaskRepository taskRepo = mock(AiTaskRepository.class);

        McpToolEventRepository.CorrelationLookupProjection lookup =
                mock(McpToolEventRepository.CorrelationLookupProjection.class);
        when(lookup.getInstanceId()).thenReturn("inst-2");
        when(lookup.getOperationId()).thenReturn("op-2");
        when(repo.findOpByCorrelationId(projectId, aiTaskId)).thenReturn(lookup);
        when(repo.findCorrelationId(projectId, "inst-2", "op-2")).thenReturn(aiTaskId);

        AiTask task = minimalTask(aiTaskId, projectId);
        when(taskRepo.findById(aiTaskId)).thenReturn(Optional.of(task));
        when(repo.findSingleOperation(projectId, "inst-2", "op-2")).thenReturn(null);

        ContextTraceService service = new ContextTraceService(repo, taskRepo);
        ContextTraceView view = service.getContextTraceByAiTaskId(projectId, aiTaskId);

        assertThat(view.instanceId()).isEqualTo("inst-2");
        assertThat(view.operationId()).isEqualTo("op-2");
    }

    @Test
    void getContextTraceByAiTaskId_throwsWhenNoCorrelationLookup() {
        UUID projectId = UUID.randomUUID();
        UUID aiTaskId = UUID.randomUUID();
        McpToolEventRepository repo = mock(McpToolEventRepository.class);
        AiTaskRepository taskRepo = mock(AiTaskRepository.class);

        when(repo.findOpByCorrelationId(projectId, aiTaskId)).thenReturn(null);

        ContextTraceService service = new ContextTraceService(repo, taskRepo);

        assertThatThrownBy(() -> service.getContextTraceByAiTaskId(projectId, aiTaskId))
                .isInstanceOf(EntityNotFoundException.class);
    }

    private static AiTask minimalTask(UUID taskId, UUID projectId) {
        Project project = mock(Project.class);
        when(project.getId()).thenReturn(projectId);

        Analysis analysis = mock(Analysis.class);
        when(analysis.getProject()).thenReturn(project);

        AiTask task = mock(AiTask.class);
        when(task.getId()).thenReturn(taskId);
        when(task.getAnalysis()).thenReturn(analysis);
        when(task.getStatus()).thenReturn(AiTaskStatus.COMPLETED);
        when(task.getContextSnapshot()).thenReturn(Map.of());
        when(task.getIntentSnapshot()).thenReturn(null);
        when(task.getSynthesisSnapshot()).thenReturn(null);
        when(task.getContextDigest()).thenReturn(null);
        when(task.getProjectionDigest()).thenReturn(null);
        when(task.getFailureCode()).thenReturn(null);
        when(task.getFailureMessage()).thenReturn(null);
        when(task.getCreatedAt()).thenReturn(Instant.parse("2026-01-01T09:00:00Z"));
        when(task.getCompletedAt()).thenReturn(Instant.parse("2026-01-01T09:00:05Z"));
        return task;
    }
}
