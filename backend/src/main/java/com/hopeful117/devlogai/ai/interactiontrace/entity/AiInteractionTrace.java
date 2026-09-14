package com.hopeful117.devlogai.ai.interactiontrace.entity;

import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.analysis.entity.Analysis;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ai_interaction_traces")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiInteractionTrace {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ai_task_id", nullable = false, updatable = false)
    private AiTask aiTask;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id", nullable = false, updatable = false)
    private Analysis analysis;

    @Column(name = "correlation_id", nullable = false, updatable = false)
    private UUID correlationId;

    @Column(nullable = false, updatable = false)
    private int attempt;

    @Column(name = "interaction_type", nullable = false, length = 40, updatable = false)
    private String interactionType;

    @Column(name = "trace_level", nullable = false, length = 20, updatable = false)
    private String traceLevel;

    @Column(nullable = false, length = 100, updatable = false)
    private String provider;

    @Column(name = "model_identifier", nullable = false, length = 255, updatable = false)
    private String modelIdentifier;

    @Column(nullable = false, length = 80, updatable = false)
    private String intent;

    @Column(name = "intent_version", nullable = false, length = 20, updatable = false)
    private String intentVersion;

    @Column(name = "prompt_version", nullable = false, length = 100, updatable = false)
    private String promptVersion;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at", nullable = false, updatable = false)
    private Instant completedAt;

    @Column(name = "duration_ms", nullable = false, updatable = false)
    private long durationMs;

    @Column(name = "validation_status", nullable = false, length = 40, updatable = false)
    private String validationStatus;

    @Column(name = "failure_category", length = 80, updatable = false)
    private String failureCategory;

    @Column(name = "retry_reason", columnDefinition = "TEXT", updatable = false)
    private String retryReason;

    @Column(name = "selected_knowledge_fingerprint", nullable = false, length = 64, updatable = false)
    private String selectedKnowledgeFingerprint;

    @Column(name = "prompt_fingerprint", nullable = false, length = 64, updatable = false)
    private String promptFingerprint;

    @Column(name = "selected_fact_count", nullable = false, updatable = false)
    private int selectedFactCount;

    @Column(name = "selected_observation_count", nullable = false, updatable = false)
    private int selectedObservationCount;

    @Column(name = "selected_insight_count", nullable = false, updatable = false)
    private int selectedInsightCount;

    @Column(name = "selected_engineering_event_count", nullable = false, updatable = false)
    private int selectedEngineeringEventCount;

    @Column(name = "grounding_fingerprint", nullable = false, length = 64, updatable = false)
    private String groundingFingerprint;

    @Column(name = "input_tokens", updatable = false)
    private Integer inputTokens;

    @Column(name = "output_tokens", updatable = false)
    private Integer outputTokens;

    @Column(name = "total_tokens", updatable = false)
    private Integer totalTokens;

    @Column(name = "trace_id", length = 100, updatable = false)
    private String traceId;

    @Column(name = "span_id", length = 100, updatable = false)
    private String spanId;

    @Column(name = "system_prompt", columnDefinition = "TEXT", updatable = false)
    private String systemPrompt;

    @Column(name = "user_prompt", columnDefinition = "TEXT", updatable = false)
    private String userPrompt;

    @Column(name = "raw_model_response", columnDefinition = "TEXT", updatable = false)
    private String rawModelResponse;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parsed_model_response", columnDefinition = "jsonb", updatable = false)
    private Object parsedModelResponse;

    @Column(name = "validation_diagnostics", columnDefinition = "TEXT", updatable = false)
    private String validationDiagnostics;

    @Column(name = "expires_at", updatable = false)
    private Instant expiresAt;
}
