package com.hopeful117.devlogai.mcptoolobservability;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentSessionServiceTest {

    @Test
    void listByProjectClampsPageSize() {
        McpToolEventRepository repo = mock(McpToolEventRepository.class);
        McpSessionLifecycleEventRepository lifecycleRepo = mock(McpSessionLifecycleEventRepository.class);
        when(repo.countSessionsByProject(any())).thenReturn(0L);
        when(repo.findSessionsByProject(any(), anyInt(), anyInt())).thenReturn(List.of());
        AgentSessionService service = new AgentSessionService(repo, lifecycleRepo);

        AgentSessionPageResponse result = service.listByProject(UUID.randomUUID(), 0, 999);

        assertThat(result.size()).isEqualTo(50);
        verify(repo).findSessionsByProject(any(), eq(0), eq(50));
    }

    @Test
    void listByProjectMapsAllFields() {
        McpToolEventRepository repo = mock(McpToolEventRepository.class);
        McpSessionLifecycleEventRepository lifecycleRepo = mock(McpSessionLifecycleEventRepository.class);
        String sessionId = "cc7d159d-a138-4b73-b14e-1e7279d013ab";
        Instant first = Instant.parse("2026-01-01T10:00:00Z");
        Instant last  = Instant.parse("2026-01-01T11:00:00Z");
        McpToolEventRepository.AgentSessionProjection proj = projection(
                sessionId, "claude-code", first, last, 10, 8, 1, 0, 1);
        when(repo.countSessionsByProject(any())).thenReturn(1L);
        when(repo.findSessionsByProject(any(), anyInt(), anyInt())).thenReturn(List.of(proj));
        when(lifecycleRepo.hasEndedEvent(any(), any())).thenReturn(false);
        AgentSessionService service = new AgentSessionService(repo, lifecycleRepo);

        AgentSessionPageResponse result = service.listByProject(UUID.randomUUID(), 0, 20);

        assertThat(result.totalSessions()).isEqualTo(1);
        AgentSessionView v = result.content().getFirst();
        assertThat(v.agentSessionId()).isEqualTo(sessionId);
        assertThat(v.agentProvider()).isEqualTo("claude-code");
        assertThat(v.firstObservedAt()).isEqualTo(first);
        assertThat(v.lastObservedAt()).isEqualTo(last);
        assertThat(v.operationCount()).isEqualTo(10);
        assertThat(v.respondedCount()).isEqualTo(8);
        assertThat(v.failedCount()).isEqualTo(1);
        assertThat(v.interruptedCount()).isEqualTo(0);
        assertThat(v.activeOperationCount()).isEqualTo(1);
    }

    @Test
    void listByProjectFiltersNullSessions() {
        McpToolEventRepository repo = mock(McpToolEventRepository.class);
        McpSessionLifecycleEventRepository lifecycleRepo = mock(McpSessionLifecycleEventRepository.class);
        when(repo.countSessionsByProject(any())).thenReturn(0L);
        when(repo.findSessionsByProject(any(), anyInt(), anyInt())).thenReturn(List.of());
        AgentSessionService service = new AgentSessionService(repo, lifecycleRepo);

        AgentSessionPageResponse result = service.listByProject(UUID.randomUUID(), 0, 20);

        assertThat(result.totalSessions()).isEqualTo(0);
        assertThat(result.content()).isEmpty();
    }

    @Test
    void computeLifecycleStatusEnded() {
        Instant last = Instant.now().minusSeconds(60);
        String status = AgentSessionService.computeLifecycleStatus(
                true, 0, last, Instant.now(),
                Duration.ofMinutes(10), Duration.ofMinutes(2));
        assertThat(status).isEqualTo("ENDED");
    }

    @Test
    void computeLifecycleStatusActiveTool() {
        Instant last = Instant.now().minusSeconds(30);
        String status = AgentSessionService.computeLifecycleStatus(
                false, 1, last, Instant.now(),
                Duration.ofMinutes(10), Duration.ofMinutes(2));
        assertThat(status).isEqualTo("ACTIVE_TOOL");
    }

    @Test
    void computeLifecycleStatusIdle() {
        Instant last = Instant.now().minusSeconds(700);
        String status = AgentSessionService.computeLifecycleStatus(
                false, 0, last, Instant.now(),
                Duration.ofMinutes(10), Duration.ofMinutes(2));
        assertThat(status).isEqualTo("IDLE");
    }

    @Test
    void computeLifecycleStatusUnknownWhenNullLastObserved() {
        String status = AgentSessionService.computeLifecycleStatus(
                false, 0, null, Instant.now(),
                Duration.ofMinutes(10), Duration.ofMinutes(2));
        assertThat(status).isEqualTo("UNKNOWN");
    }

    @Test
    void computeLifecycleStatusUnknownBetweenActiveToolAndIdle() {
        Instant last = Instant.now().minusSeconds(300);
        String status = AgentSessionService.computeLifecycleStatus(
                false, 0, last, Instant.now(),
                Duration.ofMinutes(10), Duration.ofMinutes(2));
        assertThat(status).isEqualTo("UNKNOWN");
    }

    @Test
    void computeLifecycleStatusEndedTakesPrecedenceOverActiveTool() {
        Instant last = Instant.now().minusSeconds(10);
        String status = AgentSessionService.computeLifecycleStatus(
                true, 2, last, Instant.now(),
                Duration.ofMinutes(10), Duration.ofMinutes(2));
        assertThat(status).isEqualTo("ENDED");
    }

    private static McpToolEventRepository.AgentSessionProjection projection(
            String sessionId, String provider,
            Instant first, Instant last,
            long total, long responded, long failed, long interrupted, long active) {
        return new McpToolEventRepository.AgentSessionProjection() {
            public String getAgentSessionId()      { return sessionId; }
            public String getAgentProvider()       { return provider; }
            public Instant getFirstObservedAt()    { return first; }
            public Instant getLastObservedAt()     { return last; }
            public long getOperationCount()        { return total; }
            public long getRespondedCount()        { return responded; }
            public long getFailedCount()           { return failed; }
            public long getInterruptedCount()      { return interrupted; }
            public long getActiveOperationCount()  { return active; }
        };
    }
}
