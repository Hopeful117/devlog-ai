package com.hopeful117.devlogai.mcptoolobservability;

import com.hopeful117.devlogai.project.entity.Project;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mcp_tool_events")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpToolEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false, length = 64)
    private String instanceId;

    @Column(nullable = false, length = 64)
    private String operationId;

    @Column(length = 128)
    private String toolName;

    @Column(nullable = false, length = 32)
    private String eventType;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(nullable = false)
    private int generation;

    @Column
    private Integer durationMs;

    @Column(length = 64)
    private String reasonCode;

    @Column(nullable = false, length = 64)
    private String schemaVersion;

    @Column(nullable = false)
    private Instant occurredAt;

    @Column(nullable = false)
    private Instant receivedAt;

    @Column(length = 128)
    private String agentSessionId;

    @Column(length = 128)
    private String agentProvider;

    @Column(name = "correlation_id")
    private java.util.UUID correlationId;
}
