package com.hopeful117.devlogai.mcptoolobservability;

import org.junit.jupiter.api.Test;

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
        when(repo.countSessionsByProject(any())).thenReturn(0L);
        when(repo.findSessionsByProject(any(), anyInt(), anyInt())).thenReturn(List.of());
        AgentSessionService service = new AgentSessionService(repo);

        AgentSessionPageResponse result = service.listByProject(UUID.randomUUID(), 0, 999);

        assertThat(result.size()).isEqualTo(50);
        verify(repo).findSessionsByProject(any(), eq(0), eq(50));
    }

    @Test
    void listByProjectMapsAllFields() {
        McpToolEventRepository repo = mock(McpToolEventRepository.class);
        String sessionId = "cc7d159d-a138-4b73-b14e-1e7279d013ab";
        Instant first = Instant.parse("2026-01-01T10:00:00Z");
        Instant last  = Instant.parse("2026-01-01T11:00:00Z");
        McpToolEventRepository.AgentSessionProjection proj = projection(
                sessionId, "claude-code", first, last, 10, 8, 1, 0, 1);
        when(repo.countSessionsByProject(any())).thenReturn(1L);
        when(repo.findSessionsByProject(any(), anyInt(), anyInt())).thenReturn(List.of(proj));
        AgentSessionService service = new AgentSessionService(repo);

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
        when(repo.countSessionsByProject(any())).thenReturn(0L);
        when(repo.findSessionsByProject(any(), anyInt(), anyInt())).thenReturn(List.of());
        AgentSessionService service = new AgentSessionService(repo);

        AgentSessionPageResponse result = service.listByProject(UUID.randomUUID(), 0, 20);

        assertThat(result.totalSessions()).isEqualTo(0);
        assertThat(result.content()).isEmpty();
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
