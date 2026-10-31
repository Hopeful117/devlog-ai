package com.hopeful117.devlogai.storycontextanalysis.policy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "story_context_completion_policies")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoryContextCompletionPolicy {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "policy_version", nullable = false, length = 64)
    private String policyVersion;

    @Column(nullable = false, length = 100)
    private String provider;

    @Column(name = "model_identifier", nullable = false, length = 255)
    private String modelIdentifier;

    @Column(name = "intent_id", nullable = false, length = 160)
    private String intentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "complexity_class", nullable = false, length = 32)
    private StoryContextComplexityClass complexityClass;

    @Column(name = "minimum_output_tokens", nullable = false)
    private int minimumOutputTokens;

    @Column(name = "target_output_tokens", nullable = false)
    private int targetOutputTokens;

    @Column(name = "maximum_output_tokens", nullable = false)
    private int maximumOutputTokens;

    @Column(name = "context_capacity_tokens", nullable = false)
    private int contextCapacityTokens;

    @Column(name = "measurement_certainty", nullable = false, length = 32)
    private String measurementCertainty;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
