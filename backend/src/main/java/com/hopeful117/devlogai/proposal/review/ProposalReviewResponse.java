package com.hopeful117.devlogai.proposal.review;

import com.hopeful117.devlogai.proposal.entity.ProposalStatus;
import com.hopeful117.devlogai.proposal.entity.ProposalType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ProposalReviewResponse(
        String version, UUID analysisId, UUID projectId, Counts counts, Page page,
        List<Item> items) {
    public static final String PROJECTION_VERSION = "proposal-review-v1";
    public record Counts(long total, long pending, long accepted, long rejected) { }
    public record Page(int number, int size, int totalPages, boolean hasPrevious,
                       boolean hasNext) { }
    public record Item(UUID id, UUID projectId, UUID analysisId, Integer sourceIndex,
                       ProposalType type, ProposalStatus status, Map<String, Object> payload,
                       BigDecimal confidence, List<String> evidenceReferences,
                       List<Evidence> facts, List<Evidence> observations,
                       Decision decision, ResultingInsight insight,
                       Instant createdAt, Instant decidedAt) { }
    public record Evidence(UUID id, String status, String type, String content,
                           String provenance) { }
    public record Decision(UUID id, String decision, UUID validatedBy, String comment,
                           Instant validatedAt) { }
    public record ResultingInsight(UUID id, String type, String severity, String title) { }
}
