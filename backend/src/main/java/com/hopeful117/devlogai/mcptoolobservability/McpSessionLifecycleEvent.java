package com.hopeful117.devlogai.mcptoolobservability;

import com.hopeful117.devlogai.project.entity.Project;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mcp_session_lifecycle_events")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpSessionLifecycleEvent {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false, length = 128)
    private String agentSessionId;

    @Column(nullable = false, length = 64)
    private String eventType;

    @Column(nullable = false)
    private Instant occurredAt;

    @Column(nullable = false)
    private Instant receivedAt;

    @Column(length = 128)
    private String agentProvider;
}
