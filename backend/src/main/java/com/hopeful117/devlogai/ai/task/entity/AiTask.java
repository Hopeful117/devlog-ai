package com.hopeful117.devlogai.ai.task.entity;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(
        name = "ai_tasks",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ai_task_correlation_id",
                columnNames = "correlation_id"
        )
)
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class AiTask {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id", nullable = false, updatable = false)
    private Analysis analysis;

    @Column(name = "correlation_id", nullable = false, unique = true, updatable = false)
    private UUID correlationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AiTaskType taskType;

    @Column(name = "intent_id", length = 80, updatable = false)
    private String intentId;

    @Column(name = "intent_version", length = 20, updatable = false)
    private String intentVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "intent_snapshot", updatable = false, columnDefinition = "jsonb")
    private Map<String, Object> intentSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "user_guidance_snapshot", updatable = false, columnDefinition = "jsonb")
    private Map<String, Object> userGuidanceSnapshot;

    @Column(name = "prompt_request_id", updatable = false)
    private UUID promptRequestId;

    @Column(name = "prompt_version", length = 100)
    private String promptVersion;

    @Column(name = "provider", length = 100)
    private String provider;

    @Column(name = "model_identifier", length = 255)
    private String modelIdentifier;

    @Column(name = "prompt_content_digest", length = 64)
    private String promptContentDigest;

    @Column(name = "context_digest", length = 64)
    private String contextDigest;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "selected_knowledge_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> selectedKnowledgeSnapshot;

    @Column(name = "selection_version", length = 100)
    private String selectionVersion;

    @Column(name = "selection_digest", length = 64)
    private String selectionDigest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AiTaskStatus status = AiTaskStatus.CREATED;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private Map<String, Object> contextSnapshot;

    @Column(length = 255)
    private String externalJobId;

    @Column(nullable = false)
    @Builder.Default
    private int attemptCount = 0;

    @Column(length = 100)
    private String failureCode;

    @Column(columnDefinition = "TEXT")
    private String failureMessage;

    @Column(nullable = false, updatable = false)
    @CreatedDate
    private Instant createdAt;

    private Instant submittedAt;

    private Instant startedAt;

    private Instant completedAt;
}
