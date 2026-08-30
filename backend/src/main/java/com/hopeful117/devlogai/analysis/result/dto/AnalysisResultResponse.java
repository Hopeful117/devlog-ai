package com.hopeful117.devlogai.analysis.result.dto;

import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.proposal.entity.ProposalStatus;
import com.hopeful117.devlogai.proposal.entity.ProposalType;
import com.hopeful117.devlogai.insight.entity.InsightType;
import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import com.hopeful117.devlogai.deliverable.entity.DeliverableType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AnalysisResultResponse(
        AnalysisHeader analysis,
        ExecutionStatus execution,
        ProposalsSection proposals,
        InsightsSection insights,
        DeliverablesSection deliverables,
        EvidenceSection evidence,
        List<NextAction> nextActions
) {
    // COMPLETED analysis factory
    public static AnalysisResultResponse completed(
            AnalysisHeader header,
            List<ProposalSummary> proposals,
            List<InsightSummary> insights,
            List<DeliverableSummary> deliverables,
            EvidenceSection evidence,
            List<NextAction> nextActions
    ) {
        return new AnalysisResultResponse(
                header,
                ExecutionStatus.ofSuccess(),
                proposalsSection(proposals),
                insightsSection(insights),
                deliverablesSection(deliverables),
                evidence,
                nextActions
        );
    }

    // FAILED analysis factory
    public static AnalysisResultResponse failed(
            AnalysisHeader header,
            String failureCode,
            String failureMessage
    ) {
        return new AnalysisResultResponse(
                header,
                ExecutionStatus.ofFailed(failureCode, failureMessage),
                emptyProposals(),
                emptyInsights(),
                emptyDeliverables(),
                emptyEvidence(),
                List.of(new NextAction("VIEW_DIAGNOSTICS", "View diagnostics", true))
        );
    }

    // IN_PROGRESS analysis factory
    public static AnalysisResultResponse inProgress(AnalysisHeader header) {
        return new AnalysisResultResponse(
                header,
                ExecutionStatus.ofInProgress(),
                emptyProposals(),
                emptyInsights(),
                emptyDeliverables(),
                emptyEvidence(),
                List.of()
        );
    }

    // Empty COMPLETED analysis factory
    public static AnalysisResultResponse emptyCompleted(
            AnalysisHeader header,
            EvidenceSection evidence
    ) {
        return new AnalysisResultResponse(
                header,
                ExecutionStatus.ofSuccess(),
                emptyProposals(),
                emptyInsights(),
                emptyDeliverables(),
                evidence,
                List.of()
        );
    }

    private static ProposalsSection proposalsSection(List<ProposalSummary> proposals) {
        long proposed = proposals.stream().filter(p -> p.status() == ProposalStatus.PROPOSED).count();
        long accepted = proposals.stream().filter(p -> p.status() == ProposalStatus.ACCEPTED).count();
        return new ProposalsSection(
                proposals.size(),
                Map.of("PROPOSED", proposed, "ACCEPTED", accepted),
                proposalsByType(proposals),
                proposals
        );
    }

    private static Map<String, Long> proposalsByType(List<ProposalSummary> proposals) {
        return proposals.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        p -> p.type().name(),
                        java.util.stream.Collectors.counting()
                ));
    }

    private static InsightsSection insightsSection(List<InsightSummary> insights) {
        return new InsightsSection(insights.size(), insights);
    }

    private static DeliverablesSection deliverablesSection(List<DeliverableSummary> deliverables) {
        return new DeliverablesSection(deliverables.size(), deliverables);
    }

    private static ProposalsSection emptyProposals() {
        return new ProposalsSection(0, Map.of(), Map.of(), List.of());
    }

    private static InsightsSection emptyInsights() {
        return new InsightsSection(0, List.of());
    }

    private static DeliverablesSection emptyDeliverables() {
        return new DeliverablesSection(0, List.of());
    }

    public static EvidenceSection emptyEvidence() {
        return new EvidenceSection(
                emptyCategory(), emptyCategory(), emptyCategory(), emptyCategory(),
                emptyCategory(), emptyCategory(), emptyCategory(), emptyCategory()
        );
    }

    private static EvidenceCategorySection emptyCategory() {
        return new EvidenceCategorySection(0, List.of());
    }

    public record AnalysisHeader(
            UUID id,
            UUID projectId,
            String objective,
            String scope,
            String intentId,
            String intentVersion,
            AnalysisStatus status,
            Instant startedAt,
            Instant completedAt,
            Long durationSeconds,
            List<String> sourcesAnalyzed,
            String targetRevision,
            String repositoryName
    ) {}

    public record ExecutionStatus(
            Boolean success,
            String failureCode,
            String failureMessage
    ) {
        public static ExecutionStatus ofSuccess() {
            return new ExecutionStatus(true, null, null);
        }

        public static ExecutionStatus ofFailed(String failureCode, String failureMessage) {
            return new ExecutionStatus(false, failureCode, failureMessage);
        }

        public static ExecutionStatus ofInProgress() {
            return new ExecutionStatus(null, null, null);
        }
    }

    public record ProposalsSection(
            int total,
            Map<String, Long> byStatus,
            Map<String, Long> byType,
            List<ProposalSummary> items
    ) {}

    public record ProposalSummary(
            UUID id,
            ProposalType type,
            ProposalStatus status,
            Double confidence,
            String title,
            String summary,
            List<String> evidencePreview,
            UUID proposalId,
            TrustedArtifact trustedArtifact,
            String rationale,
            String insightType,
            String deltaType,
            String context,
            String choice,
            String consequences,
            String category,
            String significance,
            List<UUID> supportingFactIds,
            List<UUID> supportingObservationIds
    ) {}

    public enum TrustedArtifactType {
        INSIGHT,
        DECISION,
        ENGINEERING_EVENT
    }

    public enum TrustedArtifactAvailability {
        AVAILABLE,
        UNAVAILABLE
    }

    public record TrustedArtifact(
            UUID id,
            TrustedArtifactType type,
            TrustedArtifactAvailability availability,
            boolean detailAvailable
    ) {}

    public record InsightsSection(
            int total,
            List<InsightSummary> items
    ) {}

    public record InsightSummary(
            UUID id,
            InsightType type,
            InsightSeverity severity,
            String title,
            String content,
            String rationale,
            Double confidence,
            List<String> evidenceReferences,
            UUID insightId
    ) {}

    public record DeliverablesSection(
            int total,
            List<DeliverableSummary> items
    ) {}

    public record DeliverableSummary(
            UUID id,
            DeliverableType type,
            String title,
            String audience,
            String status,
            Instant generatedAt,
            List<UUID> sourceInsights,
            UUID deliverableId
    ) {}

    public record EvidenceSection(
            EvidenceCategorySection facts,
            EvidenceCategorySection observations,
            EvidenceCategorySection priorInsights,
            EvidenceCategorySection architectureKnowledge,
            EvidenceCategorySection engineeringEvents,
            EvidenceCategorySection humanContext,
            EvidenceCategorySection evolutionContext,
            EvidenceCategorySection repositoryEvidence
    ) {}

    public record EvidenceCategorySection(
            int count,
            List<EvidenceItem> items
    ) {}

    public record EvidenceItem(
            String layer,
            String kind,
            String reference,
            String summary,
            Instant occurredAt,
            List<String> relatedReferences
    ) {}

    public record NextAction(
            String action,
            String label,
            boolean available
    ) {}
}
