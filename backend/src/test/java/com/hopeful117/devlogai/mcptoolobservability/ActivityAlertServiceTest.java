package com.hopeful117.devlogai.mcptoolobservability;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ActivityAlertServiceTest {

    @Test
    void listByProjectClampsPageSize() {
        McpToolEventRepository repo = mock(McpToolEventRepository.class);
        when(repo.countAlertsByProject(any())).thenReturn(0L);
        when(repo.findAlertsByProject(any(), anyInt(), anyInt())).thenReturn(List.of());
        ActivityAlertService service = new ActivityAlertService(repo);

        ActivityAlertPageResponse result = service.listByProject(UUID.randomUUID(), 0, 999);

        assertThat(result.size()).isEqualTo(50);
        verify(repo).findAlertsByProject(any(), eq(0), eq(50));
    }

    @Test
    void listByProjectReturnsTotalAndContent() {
        McpToolEventRepository repo = mock(McpToolEventRepository.class);
        when(repo.countAlertsByProject(any())).thenReturn(2L);
        when(repo.findAlertsByProject(any(), anyInt(), anyInt())).thenReturn(
                List.of(projection("inst1", "op1", "FAILED", null),
                        projection("inst2", "op2", "INTERRUPTED", "MCP_REAUTH_REQUIRED")));
        ActivityAlertService service = new ActivityAlertService(repo);

        ActivityAlertPageResponse result = service.listByProject(UUID.randomUUID(), 0, 20);

        assertThat(result.totalAlerts()).isEqualTo(2);
        assertThat(result.content()).hasSize(2);
    }

    @Test
    void toViewAssignsErrorSeverityToFailed() {
        ActivityAlertView v = ActivityAlertService.toView(projection("i", "o", "FAILED", null));
        assertThat(v.severity()).isEqualTo("ERROR");
        assertThat(v.eventType()).isEqualTo("MCP_TOOL_FAILED");
    }

    @Test
    void toViewAssignsWarnSeverityToInterrupted() {
        ActivityAlertView v = ActivityAlertService.toView(
                projection("i", "o", "INTERRUPTED", "MCP_REAUTH_REQUIRED"));
        assertThat(v.severity()).isEqualTo("WARN");
        assertThat(v.eventType()).isEqualTo("MCP_TOOL_INTERRUPTED");
    }

    @Test
    void toViewAssignsErrorSeverityToMcpProcessFailed() {
        ActivityAlertView v = ActivityAlertService.toView(
                projection("i", "o", "INTERRUPTED", "MCP_PROCESS_FAILED"));
        assertThat(v.severity()).isEqualTo("ERROR");
    }

    @Test
    void toViewAssignsWarnSeverityToNullReasonCode() {
        ActivityAlertView v = ActivityAlertService.toView(
                projection("i", "o", "INTERRUPTED", null));
        assertThat(v.severity()).isEqualTo("WARN");
    }

    @Test
    void toViewBuildsAlertId() {
        ActivityAlertView v = ActivityAlertService.toView(projection("inst1", "op1", "FAILED", null));
        assertThat(v.alertId()).isEqualTo("inst1/op1");
    }

    @Test
    void toViewPreservesNullableFields() {
        ActivityAlertView v = ActivityAlertService.toView(projection("i", "o", "FAILED", null));
        assertThat(v.toolName()).isNull();
        assertThat(v.agentSessionId()).isNull();
        assertThat(v.agentProvider()).isNull();
        assertThat(v.durationMs()).isNull();
    }

    @Test
    void listByProjectEmptyReturnsZeroTotal() {
        McpToolEventRepository repo = mock(McpToolEventRepository.class);
        when(repo.countAlertsByProject(any())).thenReturn(0L);
        when(repo.findAlertsByProject(any(), anyInt(), anyInt())).thenReturn(List.of());
        ActivityAlertService service = new ActivityAlertService(repo);

        ActivityAlertPageResponse result = service.listByProject(UUID.randomUUID(), 0, 20);

        assertThat(result.totalAlerts()).isEqualTo(0);
        assertThat(result.content()).isEmpty();
    }

    @Test
    void countErrorsUsesExactly60MinuteWindow() {
        McpToolEventRepository repo = mock(McpToolEventRepository.class);
        when(repo.countErrorAlertsByProjectInWindow(any(), any())).thenReturn(0L);
        ActivityAlertService service = new ActivityAlertService(repo);
        Instant before = Instant.now().minusSeconds(60 * 60 + 2);

        service.countErrors(UUID.randomUUID());

        ArgumentCaptor<Instant> windowCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(repo).countErrorAlertsByProjectInWindow(any(), windowCaptor.capture());
        Instant windowStart = windowCaptor.getValue();
        assertThat(windowStart).isAfter(before);
        assertThat(windowStart).isBefore(Instant.now().minusSeconds(60 * 60 - 2));
    }

    @Test
    void countErrorsReturnsOnlyErrorAlerts() {
        McpToolEventRepository repo = mock(McpToolEventRepository.class);
        when(repo.countErrorAlertsByProjectInWindow(any(), any())).thenReturn(3L);
        ActivityAlertService service = new ActivityAlertService(repo);

        ActivityAlertCountResponse result = service.countErrors(UUID.randomUUID());

        assertThat(result.errorCount()).isEqualTo(3);
    }

    @Test
    void countErrorsReturnsZeroWhenNoErrors() {
        McpToolEventRepository repo = mock(McpToolEventRepository.class);
        when(repo.countErrorAlertsByProjectInWindow(any(), any())).thenReturn(0L);
        ActivityAlertService service = new ActivityAlertService(repo);

        ActivityAlertCountResponse result = service.countErrors(UUID.randomUUID());

        assertThat(result.errorCount()).isEqualTo(0);
    }

    private static McpToolEventRepository.ActivityAlertProjection projection(
            String instanceId, String operationId, String status, String reasonCode) {
        return new McpToolEventRepository.ActivityAlertProjection() {
            public String getInstanceId()    { return instanceId; }
            public String getOperationId()   { return operationId; }
            public String getToolName()      { return null; }
            public String getStatus()        { return status; }
            public String getReasonCode()    { return reasonCode; }
            public Instant getOccurredAt()   { return Instant.parse("2026-11-16T10:00:00Z"); }
            public Integer getDurationMs()   { return null; }
            public String getAgentSessionId() { return null; }
            public String getAgentProvider() { return null; }
        };
    }
}
