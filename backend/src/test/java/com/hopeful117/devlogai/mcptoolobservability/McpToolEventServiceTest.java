package com.hopeful117.devlogai.mcptoolobservability;

import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.entity.ProjectStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class McpToolEventServiceTest {

    @Test
    void ingestSavesEventWhenNotDuplicate() {
        McpToolEventRepository repo = mock(McpToolEventRepository.class);
        when(repo.existsByInstanceIdAndOperationIdAndEventType(any(), any(), any())).thenReturn(false);
        McpToolEventService service = new McpToolEventService(repo);
        Project project = project();

        service.ingest(project, ingestRequest("inst-1", "op-1", "MCP_TOOL_RESPONDED"));

        verify(repo).insertIfAbsent(any(), any(), eq("inst-1"), eq("op-1"),
                any(), any(), any(), anyInt(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void ingestIsIdempotentForDuplicateIdentity() {
        McpToolEventRepository repo = mock(McpToolEventRepository.class);
        when(repo.existsByInstanceIdAndOperationIdAndEventType(eq("inst-1"), eq("op-1"), eq("MCP_TOOL_RESPONDED")))
                .thenReturn(true);
        McpToolEventService service = new McpToolEventService(repo);

        service.ingest(project(), ingestRequest("inst-1", "op-1", "MCP_TOOL_RESPONDED"));

        verify(repo, never()).insertIfAbsent(any(), any(), any(), any(),
                any(), any(), any(), anyInt(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void ingestFallsBackToNowWhenOccurredAtIsMalformed() {
        McpToolEventRepository repo = mock(McpToolEventRepository.class);
        when(repo.existsByInstanceIdAndOperationIdAndEventType(any(), any(), any())).thenReturn(false);
        McpToolEventService service = new McpToolEventService(repo);
        McpToolEventIngestRequest request = new McpToolEventIngestRequest(
                "devlog-ai", "inst-1", "op-1", "get_engineering_context",
                "MCP_TOOL_RESPONDED", "RESPONDED", 1, "mcp-tool-observability-v1",
                150, null, "not-a-timestamp", null, null, null);

        service.ingest(project(), request);

        verify(repo).insertIfAbsent(any(), any(), any(), any(),
                any(), any(), any(), anyInt(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void ingestFallsBackToNowWhenOccurredAtIsNull() {
        McpToolEventRepository repo = mock(McpToolEventRepository.class);
        when(repo.existsByInstanceIdAndOperationIdAndEventType(any(), any(), any())).thenReturn(false);
        McpToolEventService service = new McpToolEventService(repo);
        McpToolEventIngestRequest request = new McpToolEventIngestRequest(
                "devlog-ai", "inst-1", "op-1", null,
                "MCP_TOOL_STARTED", "ACTIVE", 1, "mcp-tool-observability-v1",
                null, null, null, null, null, null);

        service.ingest(project(), request);

        verify(repo).insertIfAbsent(any(), any(), any(), any(),
                any(), any(), any(), anyInt(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void listByProjectClampsPageSizeToFifty() {
        McpToolEventRepository repo = mock(McpToolEventRepository.class);
        org.springframework.data.domain.Page<McpToolEvent> empty =
                new org.springframework.data.domain.PageImpl<>(List.of());
        when(repo.findByProjectIdOrderByReceivedAtDesc(any(), any())).thenReturn(empty);
        McpToolEventService service = new McpToolEventService(repo);
        UUID projectId = UUID.randomUUID();

        McpToolEventPageResponse result = service.listByProject(projectId, 0, 999);

        assertThat(result.size()).isEqualTo(50);
        verify(repo).findByProjectIdOrderByReceivedAtDesc(eq(projectId),
                argThat(p -> p.getPageSize() == 50));
    }

    @Test
    void listByProjectMapsEventFieldsToResponse() {
        McpToolEventRepository repo = mock(McpToolEventRepository.class);
        UUID eventId = UUID.randomUUID();
        McpToolEvent event = McpToolEvent.builder()
                .id(eventId)
                .instanceId("inst-1")
                .operationId("op-1")
                .toolName("get_engineering_context")
                .eventType("MCP_TOOL_RESPONDED")
                .status("RESPONDED")
                .generation(1)
                .durationMs(200)
                .occurredAt(java.time.Instant.parse("2026-01-01T12:00:00Z"))
                .receivedAt(java.time.Instant.now())
                .build();
        org.springframework.data.domain.Page<McpToolEvent> page =
                new org.springframework.data.domain.PageImpl<>(List.of(event));
        when(repo.findByProjectIdOrderByReceivedAtDesc(any(), any())).thenReturn(page);
        McpToolEventService service = new McpToolEventService(repo);

        McpToolEventPageResponse result = service.listByProject(UUID.randomUUID(), 0, 20);

        assertThat(result.content()).hasSize(1);
        McpToolEventResponse dto = result.content().get(0);
        assertThat(dto.id()).isEqualTo(eventId);
        assertThat(dto.toolName()).isEqualTo("get_engineering_context");
        assertThat(dto.status()).isEqualTo("RESPONDED");
        assertThat(dto.durationMs()).isEqualTo(200);
    }

    private static Project project() {
        return Project.builder()
                .id(UUID.randomUUID())
                .name("devlog-ai")
                .slug("devlog-ai")
                .status(ProjectStatus.ACTIVE)
                .build();
    }

    private static McpToolEventIngestRequest ingestRequest(
            String instanceId, String operationId, String eventType) {
        return new McpToolEventIngestRequest(
                "devlog-ai", instanceId, operationId, "get_engineering_context",
                eventType, "RESPONDED", 1, "mcp-tool-observability-v1",
                100, null, "2026-01-01T12:00:00Z", null, null, null);
    }
}
