package com.hopeful117.devlogai.mcptoolobservability;

import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.entity.ProjectStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class McpSessionLifecycleEventServiceTest {

    @Test
    void ingestPersistsEvent() {
        McpSessionLifecycleEventRepository repo = mock(McpSessionLifecycleEventRepository.class);
        McpSessionLifecycleEventService service = new McpSessionLifecycleEventService(repo);

        service.ingest(project(), request("session-1", "SESSION_LIFECYCLE_ENDED", null, null));

        verify(repo).insertIfAbsent(any(), any(), eq("session-1"), eq("SESSION_LIFECYCLE_ENDED"),
                any(), any(), isNull());
    }

    @Test
    void ingestDelegatesInsertTwiceForTwoCalls() {
        McpSessionLifecycleEventRepository repo = mock(McpSessionLifecycleEventRepository.class);
        McpSessionLifecycleEventService service = new McpSessionLifecycleEventService(repo);

        service.ingest(project(), request("session-1", "SESSION_LIFECYCLE_ENDED", null, null));
        service.ingest(project(), request("session-1", "SESSION_LIFECYCLE_ENDED", null, null));

        verify(repo, times(2)).insertIfAbsent(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void ingestFallsBackToNowWhenOccurredAtIsMalformed() {
        McpSessionLifecycleEventRepository repo = mock(McpSessionLifecycleEventRepository.class);
        McpSessionLifecycleEventService service = new McpSessionLifecycleEventService(repo);

        service.ingest(project(), request("s1", "SESSION_LIFECYCLE_ENDED", "not-a-timestamp", "cc"));

        verify(repo).insertIfAbsent(any(), any(), eq("s1"), eq("SESSION_LIFECYCLE_ENDED"),
                any(), any(), eq("cc"));
    }

    @Test
    void ingestFallsBackToNowWhenOccurredAtIsNull() {
        McpSessionLifecycleEventRepository repo = mock(McpSessionLifecycleEventRepository.class);
        McpSessionLifecycleEventService service = new McpSessionLifecycleEventService(repo);

        service.ingest(project(), request("s1", "SESSION_LIFECYCLE_ENDED", null, "cc"));

        verify(repo).insertIfAbsent(any(), any(), any(), any(), any(), any(), eq("cc"));
    }

    private static Project project() {
        return Project.builder()
                .id(UUID.randomUUID())
                .name("test")
                .slug("test")
                .status(ProjectStatus.ACTIVE)
                .build();
    }

    private static McpSessionLifecycleEventIngestRequest request(
            String sessionId, String eventType, String occurredAt, String provider) {
        return new McpSessionLifecycleEventIngestRequest(
                "test", sessionId, eventType, occurredAt, provider);
    }
}
