package com.hopeful117.devlogai.ai.engine.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_task_callback_rejections")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiTaskCallbackRejection {
    @Id
    private UUID id;

    @Column(name = "ai_task_id", nullable = false, updatable = false)
    private UUID aiTaskId;

    @Column(name = "correlation_id", updatable = false)
    private UUID correlationId;

    @Column(name = "result_status", length = 30, updatable = false)
    private String resultStatus;

    @Column(name = "http_status", nullable = false, updatable = false)
    private int httpStatus;

    @Column(name = "error_code", nullable = false, length = 100, updatable = false)
    private String errorCode;

    @Column(name = "current_status", length = 30, updatable = false)
    private String currentStatus;

    @Column(name = "error_message", columnDefinition = "TEXT", updatable = false)
    private String errorMessage;

    @Column(name = "interaction_trace_count", nullable = false, updatable = false)
    private int interactionTraceCount;

    @Column(name = "provider_generation_count", updatable = false)
    private Integer providerGenerationCount;

    @Column(name = "provider_elapsed_millis", updatable = false)
    private Long providerElapsedMillis;

    @Column(name = "provider_output_tokens", updatable = false)
    private Long providerOutputTokens;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;
}
