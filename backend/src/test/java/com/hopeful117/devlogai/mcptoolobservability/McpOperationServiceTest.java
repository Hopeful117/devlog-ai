package com.hopeful117.devlogai.mcptoolobservability;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class McpOperationServiceTest {

    @Test
    void listByProjectClampsPageSizeToFifty() {
        McpToolEventRepository repo = mockRepo(List.of(), 0L);
        McpOperationService service = new McpOperationService(repo);

        McpOperationPageResponse result = service.listByProject(UUID.randomUUID(), 0, 999);

        assertThat(result.size()).isEqualTo(50);
        verify(repo).findOperationsByProject(any(), eq(0), eq(50));
    }

    @Test
    void listByProjectClampsNegativePageToZero() {
        McpToolEventRepository repo = mockRepo(List.of(), 0L);
        McpOperationService service = new McpOperationService(repo);
        UUID projectId = UUID.randomUUID();

        McpOperationPageResponse result = service.listByProject(projectId, -5, 10);

        assertThat(result.page()).isEqualTo(0);
        verify(repo).findOperationsByProject(eq(projectId), eq(0), eq(10));
    }

    @Test
    void listByProjectPassesCorrectOffset() {
        McpToolEventRepository repo = mockRepo(List.of(), 0L);
        McpOperationService service = new McpOperationService(repo);
        UUID projectId = UUID.randomUUID();

        service.listByProject(projectId, 2, 10);

        verify(repo).findOperationsByProject(eq(projectId), eq(20), eq(10));
    }

    @Test
    void listByProjectMapsProjectionToView() {
        Instant started = Instant.parse("2026-01-01T12:00:00Z");
        Instant completed = Instant.parse("2026-01-01T12:00:01Z");
        McpToolEventRepository.McpOperationProjection proj = projection(
                "inst-1", "op-1", "search_project_history",
                "RESPONDED", 1, started, completed, 1050, null);
        McpToolEventRepository repo = mockRepo(List.of(proj), 1L);
        McpOperationService service = new McpOperationService(repo);

        McpOperationPageResponse result = service.listByProject(UUID.randomUUID(), 0, 20);

        assertThat(result.totalOperations()).isEqualTo(1L);
        assertThat(result.content()).hasSize(1);
        McpOperationView view = result.content().getFirst();
        assertThat(view.instanceId()).isEqualTo("inst-1");
        assertThat(view.operationId()).isEqualTo("op-1");
        assertThat(view.toolName()).isEqualTo("search_project_history");
        assertThat(view.status()).isEqualTo("RESPONDED");
        assertThat(view.generation()).isEqualTo(1);
        assertThat(view.startedAt()).isEqualTo(started);
        assertThat(view.completedAt()).isEqualTo(completed);
        assertThat(view.durationMs()).isEqualTo(1050);
        assertThat(view.reasonCode()).isNull();
    }

    @Test
    void listByProjectMapsActiveOperationWithNulls() {
        Instant started = Instant.parse("2026-01-01T12:00:00Z");
        McpToolEventRepository.McpOperationProjection proj = projection(
                "inst-2", "op-2", "get_engineering_context",
                "ACTIVE", 1, started, null, null, null);
        McpToolEventRepository repo = mockRepo(List.of(proj), 1L);
        McpOperationService service = new McpOperationService(repo);

        McpOperationPageResponse result = service.listByProject(UUID.randomUUID(), 0, 20);

        McpOperationView view = result.content().getFirst();
        assertThat(view.status()).isEqualTo("ACTIVE");
        assertThat(view.completedAt()).isNull();
        assertThat(view.durationMs()).isNull();
    }

    @Test
    void listByProjectDefaultsNullGenerationToZero() {
        McpToolEventRepository.McpOperationProjection proj = projection(
                "inst-3", "op-3", null, "ACTIVE", null, null, null, null, null);
        McpToolEventRepository repo = mockRepo(List.of(proj), 1L);
        McpOperationService service = new McpOperationService(repo);

        McpOperationPageResponse result = service.listByProject(UUID.randomUUID(), 0, 20);

        assertThat(result.content().getFirst().generation()).isEqualTo(0);
    }

    private static McpToolEventRepository mockRepo(
            List<McpToolEventRepository.McpOperationProjection> rows, long count) {
        McpToolEventRepository repo = mock(McpToolEventRepository.class);
        when(repo.findOperationsByProject(any(), anyInt(), anyInt())).thenReturn(rows);
        when(repo.countOperationsByProject(any())).thenReturn(count);
        return repo;
    }

    private static McpToolEventRepository.McpOperationProjection projection(
            String instanceId, String operationId, String toolName,
            String status, Integer generation,
            Instant startedAt, Instant completedAt,
            Integer durationMs, String reasonCode) {
        return new McpToolEventRepository.McpOperationProjection() {
            public String getInstanceId() { return instanceId; }
            public String getOperationId() { return operationId; }
            public String getToolName() { return toolName; }
            public String getStatus() { return status; }
            public Integer getGeneration() { return generation; }
            public Instant getStartedAt() { return startedAt; }
            public Instant getCompletedAt() { return completedAt; }
            public Integer getDurationMs() { return durationMs; }
            public String getReasonCode() { return reasonCode; }
        };
    }
}
