package com.hopeful117.devlogai.mcptoolobservability;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ActivityIncidentServiceTest {

    @Test
    void listByProjectReturnsEmptyWhenNoRows() {
        McpToolEventRepository repo = mock(McpToolEventRepository.class);
        when(repo.countIncidentsByProject(any(), any())).thenReturn(0L);
        when(repo.findIncidentsByProject(any(), any(), anyInt(), anyInt())).thenReturn(List.of());
        ActivityIncidentService service = new ActivityIncidentService(repo);

        ActivityIncidentPageResponse result = service.listByProject(UUID.randomUUID(), 60, 0, 20);

        assertThat(result.totalIncidents()).isEqualTo(0);
        assertThat(result.content()).isEmpty();
        assertThat(result.errorCount()).isEqualTo(0);
        assertThat(result.warnCount()).isEqualTo(0);
    }

    @Test
    void listByProjectClampsWindowMinutes() {
        McpToolEventRepository repo = mock(McpToolEventRepository.class);
        when(repo.countIncidentsByProject(any(), any())).thenReturn(0L);
        when(repo.findIncidentsByProject(any(), any(), anyInt(), anyInt())).thenReturn(List.of());
        ActivityIncidentService service = new ActivityIncidentService(repo);

        ActivityIncidentPageResponse result = service.listByProject(UUID.randomUUID(), 9999, 0, 20);

        assertThat(result.windowMinutes()).isEqualTo(1440);
    }

    @Test
    void listByProjectClampsWindowMinutesMin() {
        McpToolEventRepository repo = mock(McpToolEventRepository.class);
        when(repo.countIncidentsByProject(any(), any())).thenReturn(0L);
        when(repo.findIncidentsByProject(any(), any(), anyInt(), anyInt())).thenReturn(List.of());
        ActivityIncidentService service = new ActivityIncidentService(repo);

        ActivityIncidentPageResponse result = service.listByProject(UUID.randomUUID(), 0, 0, 20);

        assertThat(result.windowMinutes()).isEqualTo(1);
    }

    @Test
    void listByProjectClampsSize() {
        McpToolEventRepository repo = mock(McpToolEventRepository.class);
        when(repo.countIncidentsByProject(any(), any())).thenReturn(0L);
        when(repo.findIncidentsByProject(any(), any(), anyInt(), anyInt())).thenReturn(List.of());
        ActivityIncidentService service = new ActivityIncidentService(repo);

        service.listByProject(UUID.randomUUID(), 60, 0, 999);

        verify(repo).findIncidentsByProject(any(), any(), eq(0), eq(50));
    }

    @Test
    void listByProjectAssignsErrorSeverity() {
        McpToolEventRepository repo = mock(McpToolEventRepository.class);
        when(repo.countIncidentsByProject(any(), any())).thenReturn(1L);
        when(repo.findIncidentsByProject(any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(projection("tool", null, 3, true, "inst1/op1,inst2/op2")));
        ActivityIncidentService service = new ActivityIncidentService(repo);

        ActivityIncidentPageResponse result = service.listByProject(UUID.randomUUID(), 60, 0, 20);

        assertThat(result.content().getFirst().severity()).isEqualTo("ERROR");
        assertThat(result.errorCount()).isEqualTo(1);
        assertThat(result.warnCount()).isEqualTo(0);
    }

    @Test
    void listByProjectAssignsWarnSeverity() {
        McpToolEventRepository repo = mock(McpToolEventRepository.class);
        when(repo.countIncidentsByProject(any(), any())).thenReturn(1L);
        when(repo.findIncidentsByProject(any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(projection("tool", "MCP_REAUTH_REQUIRED", 2, false, "inst1/op1")));
        ActivityIncidentService service = new ActivityIncidentService(repo);

        ActivityIncidentPageResponse result = service.listByProject(UUID.randomUUID(), 60, 0, 20);

        assertThat(result.content().getFirst().severity()).isEqualTo("WARN");
        assertThat(result.warnCount()).isEqualTo(1);
    }

    @Test
    void toViewSetsOccurrenceCountAndDates() {
        Instant first = Instant.parse("2026-11-16T10:00:00Z");
        Instant last  = Instant.parse("2026-11-16T10:30:00Z");
        ActivityIncidentView v = ActivityIncidentService.toView(
                projectionWithDates("my_tool", null, 7, true, "a/b", first, last));

        assertThat(v.occurrenceCount()).isEqualTo(7);
        assertThat(v.firstOccurredAt()).isEqualTo(first);
        assertThat(v.lastOccurredAt()).isEqualTo(last);
    }

    @Test
    void computeIncidentIdIsStable() {
        String id1 = ActivityIncidentService.computeIncidentId("tool_a", "REASON_X");
        String id2 = ActivityIncidentService.computeIncidentId("tool_a", "REASON_X");
        assertThat(id1).isEqualTo(id2).hasSize(16);
    }

    @Test
    void computeIncidentIdHandlesNulls() {
        String idNullNull  = ActivityIncidentService.computeIncidentId(null, null);
        String idEmptyNull = ActivityIncidentService.computeIncidentId("", null);
        String idNullEmpty = ActivityIncidentService.computeIncidentId(null, "");
        assertThat(idNullNull).hasSize(16);
        assertThat(idNullNull).isNotEqualTo(idEmptyNull);
        assertThat(idNullNull).isNotEqualTo(idNullEmpty);
        assertThat(idEmptyNull).isNotEqualTo(idNullEmpty);
    }

    @Test
    void parseRecentAlertIdsLimitsToThree() {
        List<String> ids = ActivityIncidentService.parseRecentAlertIds("a/1,b/2,c/3,d/4,e/5");
        assertThat(ids).hasSize(3).containsExactly("a/1", "b/2", "c/3");
    }

    @Test
    void parseRecentAlertIdsHandlesNull() {
        assertThat(ActivityIncidentService.parseRecentAlertIds(null)).isEmpty();
    }

    // --- helpers ---

    private static McpToolEventRepository.ActivityIncidentProjection projection(
            String toolName, String reasonCode, long count, boolean hasError, String recentRaw) {
        Instant now = Instant.now();
        return projectionWithDates(toolName, reasonCode, count, hasError, recentRaw, now, now);
    }

    private static McpToolEventRepository.ActivityIncidentProjection projectionWithDates(
            String toolName, String reasonCode, long count, boolean hasError, String recentRaw,
            Instant first, Instant last) {
        return new McpToolEventRepository.ActivityIncidentProjection() {
            public String getToolName()          { return toolName; }
            public String getReasonCode()        { return reasonCode; }
            public long getOccurrenceCount()     { return count; }
            public Instant getFirstOccurredAt()  { return first; }
            public Instant getLastOccurredAt()   { return last; }
            public boolean isHasError()          { return hasError; }
            public String getRecentAlertIdsRaw() { return recentRaw; }
        };
    }
}
