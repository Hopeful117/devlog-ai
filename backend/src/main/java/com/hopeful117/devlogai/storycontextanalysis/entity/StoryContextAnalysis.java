package com.hopeful117.devlogai.storycontextanalysis.entity;

import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.story.entity.EngineeringStory;
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
@Table(name = "story_context_analyses")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class StoryContextAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "story_id", nullable = false)
    private EngineeringStory story;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ai_task_id", nullable = false, unique = true)
    private AiTask aiTask;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "analysis_snapshot", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> analysisSnapshot;

    @Column(name = "context_digest", length = 64, nullable = false)
    private String contextDigest;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "prompt_execution_metadata", columnDefinition = "jsonb")
    private Map<String, Object> promptExecutionMetadata;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}