package com.hopeful117.devlogai.temporal.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Domain object representing the temporal freshness assessment of a trusted Insight.
 *
 * This is a derived model — NOT a persisted entity. No JPA annotations, no database table,
 * no Flyway migration. The assessment is computed on-demand from existing state.
 *
 * Invariants:
 * - authority (InsightStatus) is separate from freshness (conclusion)
 * - conclusion must NOT be CURRENT when evidence is insufficient
 * - CURRENT requires positive verification at both baseline AND currentKnownRevision
 * - reasoningOrigin is always DETERMINISTIC in V1
 * - NOT_APPLICABLE for non-ACTIVE Insights (rejected at assessment boundary)
 */
public class TemporalAssessment {

    private final UUID insightId;
    private final Conclusion conclusion;
    private final ReasoningOrigin reasoningOrigin;
    private final List<String> supportingEvidence;
    private final Instant evaluatedAt;

    private TemporalAssessment(UUID insightId, Conclusion conclusion,
            ReasoningOrigin reasoningOrigin, List<String> supportingEvidence,
            Instant evaluatedAt) {
        this.insightId = insightId;
        this.conclusion = conclusion;
        this.reasoningOrigin = reasoningOrigin;
        this.supportingEvidence = List.copyOf(supportingEvidence);
        this.evaluatedAt = evaluatedAt;
    }

    public static TemporalAssessment of(UUID insightId,
            List<String> supportingEvidence, Conclusion conclusion,
            ReasoningOrigin reasoningOrigin) {
        return new TemporalAssessment(insightId, conclusion, reasoningOrigin,
                supportingEvidence, Instant.now());
    }

    public UUID getInsightId() { return insightId; }
    public Conclusion getConclusion() { return conclusion; }
    public ReasoningOrigin getReasoningOrigin() { return reasoningOrigin; }
    public List<String> getSupportingEvidence() { return supportingEvidence; }
    public Instant getEvaluatedAt() { return evaluatedAt; }

    public enum Conclusion { CURRENT, SUSPECTED_STALE, UNKNOWN }
    public enum ReasoningOrigin { DETERMINISTIC }
}
