package com.hopeful117.devlogai.analysis.result.service;

import com.hopeful117.devlogai.analysis.result.dto.AnalysisResultResponse;
import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.ai.task.repository.AiTaskRepository;
import com.hopeful117.devlogai.proposal.entity.ProposalStatus;
import com.hopeful117.devlogai.proposal.entity.ProposalType;
import com.hopeful117.devlogai.proposal.entity.ValidatableProposal;
import com.hopeful117.devlogai.proposal.repository.ValidatableProposalRepository;
import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import com.hopeful117.devlogai.insight.entity.InsightStatus;
import com.hopeful117.devlogai.insight.entity.InsightType;
import com.hopeful117.devlogai.insight.repository.InsightRepository;
import com.hopeful117.devlogai.deliverable.entity.DeliverableType;
import com.hopeful117.devlogai.deliverable.entity.GeneratedDeliverable;
import com.hopeful117.devlogai.deliverable.repository.GeneratedDeliverableRepository;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse;
import com.hopeful117.devlogai.analysis.evidence.service.AiTaskSelectedEvidenceService;
import com.hopeful117.devlogai.analysis.diagnostics.service.AnalysisDiagnosticsService;
import com.hopeful117.devlogai.intent.service.IntentCatalog;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.repository.SourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalysisResultQueryServiceImpl implements AnalysisResultQueryService {

    private static final int EVIDENCE_PREVIEW_LIMIT = 5;

    private final AnalysisRepository analysisRepository;
    private final AiTaskRepository aiTaskRepository;
    private final ValidatableProposalRepository proposalRepository;
    private final InsightRepository insightRepository;
    private final GeneratedDeliverableRepository deliverableRepository;
    private final SourceRepository sourceRepository;
    private final AiTaskSelectedEvidenceService selectedEvidenceService;
    private final AnalysisDiagnosticsService diagnosticsService;
    private final IntentCatalog intentCatalog;

    @Override
    public AnalysisResultResponse getResult(UUID analysisId) {
        Analysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new IllegalArgumentException("Analysis not found: " + analysisId));

        return switch (analysis.getStatus()) {
            case COMPLETED -> buildCompletedResult(analysis);
            case FAILED -> buildFailedResult(analysis);
            case IN_PROGRESS -> buildInProgressResult(analysis);
            default -> buildInProgressResult(analysis); // PENDING treated as in progress
        };
    }

    private AnalysisResultResponse buildCompletedResult(Analysis analysis) {
        IntentDefinition intent = intentCatalog.resolve(analysis.getIntentId(), analysis.getIntentVersion());
        String objective = intent != null ? intent.objective() : "Unknown objective";
        String scope = resolveScope(intent);

        AnalysisResultResponse.AnalysisHeader header = buildHeader(analysis, objective, scope);
        List<AnalysisResultResponse.ProposalSummary> proposals = buildProposals(analysis);
        List<AnalysisResultResponse.InsightSummary> insights = buildInsights(analysis);
        List<AnalysisResultResponse.DeliverableSummary> deliverables = buildDeliverables(analysis);
        AnalysisResultResponse.EvidenceSection evidence = buildEvidence(analysis);
        List<AnalysisResultResponse.NextAction> nextActions = buildNextActions(analysis, proposals, insights);

        return AnalysisResultResponse.completed(header, proposals, insights, deliverables, evidence, nextActions);
    }

    private AnalysisResultResponse buildFailedResult(Analysis analysis) {
        IntentDefinition intent = intentCatalog.resolve(analysis.getIntentId(), analysis.getIntentVersion());
        String objective = intent != null ? intent.objective() : "Unknown objective";
        String scope = resolveScope(intent);

        AnalysisResultResponse.AnalysisHeader header = buildHeader(analysis, objective, scope);
        String failureCode = extractFailureCode(analysis);
        String failureMessage = extractFailureMessage(analysis);

        return AnalysisResultResponse.failed(header, failureCode, failureMessage);
    }

    private AnalysisResultResponse buildInProgressResult(Analysis analysis) {
        IntentDefinition intent = intentCatalog.resolve(analysis.getIntentId(), analysis.getIntentVersion());
        String objective = intent != null ? intent.objective() : "Unknown objective";
        String scope = resolveScope(intent);

        AnalysisResultResponse.AnalysisHeader header = buildHeader(analysis, objective, scope);
        return AnalysisResultResponse.inProgress(header);
    }

    private String resolveScope(IntentDefinition intent) {
        if (intent == null) return "PROJECT_SCOPE";
        return switch (intent.id()) {
            case "generate-readme-v1" -> "REPOSITORY_SCOPE";
            default -> "PROJECT_SCOPE";
        };
    }

    private AnalysisResultResponse.AnalysisHeader buildHeader(Analysis analysis, String objective, String scope) {
        List<String> sourcesAnalyzed = getAnalyzedSourceNames(analysis);
        String repositoryName = null;
        if ("REPOSITORY_SCOPE".equals(scope) && analysis.getSelectedSource() != null) {
            repositoryName = analysis.getSelectedSource().getName();
        }

        Long durationSeconds = null;
        if (analysis.getStartedAt() != null && analysis.getCompletedAt() != null) {
            durationSeconds = java.time.Duration.between(analysis.getStartedAt(), analysis.getCompletedAt()).getSeconds();
        }

        return new AnalysisResultResponse.AnalysisHeader(
                analysis.getId(),
                analysis.getProject().getId(),
                objective,
                scope,
                analysis.getIntentId(),
                analysis.getIntentVersion(),
                analysis.getStatus(),
                analysis.getStartedAt(),
                analysis.getCompletedAt(),
                durationSeconds,
                sourcesAnalyzed,
                analysis.getTargetRevision(),
                repositoryName
        );
    }

    private List<String> getAnalyzedSourceNames(Analysis analysis) {
        if (analysis.getSelectedSource() != null) {
            return List.of(analysis.getSelectedSource().getName());
        }
        // For project scope, get all active sources
        return sourceRepository.findByProjectIdAndActiveTrueOrderByCreatedAtAscIdAsc(analysis.getProject().getId()).stream()
                .map(Source::getName)
                .collect(Collectors.toList());
    }

    private List<AnalysisResultResponse.ProposalSummary> buildProposals(Analysis analysis) {
        List<ValidatableProposal> proposals = proposalRepository.findByAnalysisId(analysis.getId());
        // Sort by createdAt desc
        proposals.sort(Comparator.comparing(ValidatableProposal::getCreatedAt).reversed());
        return proposals.stream()
                .filter(p -> p.getStatus() == ProposalStatus.PROPOSED || p.getStatus() == ProposalStatus.ACCEPTED)
                .map(this::mapProposal)
                .collect(Collectors.toList());
    }

    private AnalysisResultResponse.ProposalSummary mapProposal(ValidatableProposal proposal) {
        String title = extractTitle(proposal.getPayload());
        String summary = extractSummary(proposal.getPayload());
        List<String> evidencePreview = buildEvidencePreview(proposal);

        return new AnalysisResultResponse.ProposalSummary(
                proposal.getId(),
                proposal.getType(),
                proposal.getStatus(),
                proposal.getConfidence() != null ? proposal.getConfidence().doubleValue() : null,
                title,
                summary,
                evidencePreview,
                proposal.getId()
        );
    }

    private String extractTitle(Map<String, Object> payload) {
        if (payload == null) return "Untitled proposal";
        Object title = payload.get("title");
        return title instanceof String ? (String) title : "Untitled proposal";
    }

    private String extractSummary(Map<String, Object> payload) {
        if (payload == null) return "";
        Object summary = payload.get("summary");
        return summary instanceof String ? (String) summary : "";
    }

    private List<String> buildEvidencePreview(ValidatableProposal proposal) {
        List<String> preview = new ArrayList<>();
        if (proposal.getSupportingFactIds() != null) {
            for (UUID factId : proposal.getSupportingFactIds().stream().limit(3).toList()) {
                preview.add("Fact#" + factId.toString().substring(0, 8));
            }
        }
        if (proposal.getSupportingObservationIds() != null) {
            for (UUID obsId : proposal.getSupportingObservationIds().stream().limit(2).toList()) {
                preview.add("Observation#" + obsId.toString().substring(0, 8));
            }
        }
        return preview;
    }

    private List<AnalysisResultResponse.InsightSummary> buildInsights(Analysis analysis) {
        List<Insight> insights = insightRepository.findByAnalysisIdOrderByCreatedAtDesc(analysis.getId());
        return insights.stream()
                .filter(i -> i.getStatus() == InsightStatus.ACTIVE)
                .map(this::mapInsight)
                .collect(Collectors.toList());
    }

    private AnalysisResultResponse.InsightSummary mapInsight(Insight insight) {
        return new AnalysisResultResponse.InsightSummary(
                insight.getId(),
                insight.getType(),
                insight.getSeverity(),
                insight.getTitle(),
                insight.getContent(),
                insight.getRationale(),
                insight.getConfidence() != null ? insight.getConfidence().doubleValue() : null,
                insight.getEvidenceReferences() != null ? insight.getEvidenceReferences() : List.of(),
                insight.getId()
        );
    }

    private List<AnalysisResultResponse.DeliverableSummary> buildDeliverables(Analysis analysis) {
        List<GeneratedDeliverable> deliverables = deliverableRepository.findByAnalysisIdOrderByGeneratedAtDesc(analysis.getId());
        return deliverables.stream()
                .map(this::mapDeliverable)
                .collect(Collectors.toList());
    }

    private AnalysisResultResponse.DeliverableSummary mapDeliverable(GeneratedDeliverable deliverable) {
        return new AnalysisResultResponse.DeliverableSummary(
                deliverable.getId(),
                deliverable.getType(),
                deliverable.getTitle(),
                deliverable.getAudience(),
                "GENERATED",
                deliverable.getGeneratedAt(),
                deliverable.getSourceInsights() != null
                        ? deliverable.getSourceInsights().stream().map(i -> i.getId()).collect(Collectors.toList())
                        : List.of(),
                deliverable.getId()
        );
    }

    private AnalysisResultResponse.EvidenceSection buildEvidence(Analysis analysis) {
        AiTaskSelectedEvidenceResponse selectedEvidence = selectedEvidenceService.getSelectedEvidence(analysis.getId());

        if (selectedEvidence.state() != AiTaskSelectedEvidenceResponse.State.AVAILABLE) {
            return AnalysisResultResponse.emptyEvidence();
        }

        AiTaskSelectedEvidenceResponse.Categories categories = selectedEvidence.categories();
        return new AnalysisResultResponse.EvidenceSection(
                curateCategory(categories.facts()),
                curateCategory(categories.observations()),
                curateCategory(categories.priorInsights()),
                curateCategory(categories.architectureKnowledge()),
                curateCategory(categories.engineeringEvents()),
                curateCategory(categories.humanContext()),
                curateCategory(categories.evolutionContext()),
                curateCategory(categories.repositoryEvidence())
        );
    }

    private AnalysisResultResponse.EvidenceCategorySection curateCategory(
            AiTaskSelectedEvidenceResponse.FactsSection section
    ) {
        return curateCategory(section.availability(), section.count(), section.items());
    }

    private AnalysisResultResponse.EvidenceCategorySection curateCategory(
            AiTaskSelectedEvidenceResponse.ObservationsSection section
    ) {
        return curateCategory(section.availability(), section.count(), section.items());
    }

    private AnalysisResultResponse.EvidenceCategorySection curateCategory(
            AiTaskSelectedEvidenceResponse.PriorInsightsSection section
    ) {
        return curateCategory(section.availability(), section.count(), section.items());
    }

    private AnalysisResultResponse.EvidenceCategorySection curateCategory(
            AiTaskSelectedEvidenceResponse.ArchitectureKnowledgeSection section
    ) {
        return curateCategory(section.availability(), section.count(), section.items());
    }

    private AnalysisResultResponse.EvidenceCategorySection curateCategory(
            AiTaskSelectedEvidenceResponse.EngineeringEventsSection section
    ) {
        return curateCategory(section.availability(), section.count(), section.items());
    }

    private AnalysisResultResponse.EvidenceCategorySection curateCategory(
            AiTaskSelectedEvidenceResponse.HumanContextSection section
    ) {
        return curateCategory(section.availability(), section.count(), section.items());
    }

    private AnalysisResultResponse.EvidenceCategorySection curateCategory(
            AiTaskSelectedEvidenceResponse.EvolutionContextSection section
    ) {
        return curateCategory(section.availability(), section.count(), section.items());
    }

    private AnalysisResultResponse.EvidenceCategorySection curateCategory(
            AiTaskSelectedEvidenceResponse.RepositoryEvidenceSection section
    ) {
        return curateCategory(section.availability(), section.count(), section.items());
    }

    private AnalysisResultResponse.EvidenceCategorySection curateCategory(
            AiTaskSelectedEvidenceResponse.Availability availability,
            int count,
            List<?> items
    ) {
        if (availability == AiTaskSelectedEvidenceResponse.Availability.NOT_RECORDED || items == null) {
            return new AnalysisResultResponse.EvidenceCategorySection(0, List.of());
        }

        @SuppressWarnings("unchecked")
        List<AnalysisResultResponse.EvidenceItem> evidenceItems = (List<AnalysisResultResponse.EvidenceItem>) items;
        List<AnalysisResultResponse.EvidenceItem> limited = evidenceItems.stream()
                .limit(EVIDENCE_PREVIEW_LIMIT)
                .collect(Collectors.toList());

        return new AnalysisResultResponse.EvidenceCategorySection(count, limited);
    }

    private List<AnalysisResultResponse.NextAction> buildNextActions(Analysis analysis,
                                                                     List<AnalysisResultResponse.ProposalSummary> proposals,
                                                                     List<AnalysisResultResponse.InsightSummary> insights) {
        List<AnalysisResultResponse.NextAction> actions = new ArrayList<>();

        long proposedCount = proposals.stream()
                .filter(p -> p.status() == com.hopeful117.devlogai.proposal.entity.ProposalStatus.PROPOSED)
                .count();
        if (proposedCount > 0) {
            actions.add(new AnalysisResultResponse.NextAction(
                    "REVIEW_PROPOSALS",
                    "Review " + proposedCount + " pending proposal" + (proposedCount != 1 ? "s" : ""),
                    true
            ));
        }

        boolean hasActiveInsights = insights.stream()
                .anyMatch(i -> i.type() != null);
        boolean deliverableEligible = hasActiveInsights; // Simplified: domain rules would be checked here
        if (deliverableEligible) {
            actions.add(new AnalysisResultResponse.NextAction(
                    "GENERATE_DELIVERABLE",
                    "Generate deliverable",
                    true
            ));
        }

        actions.add(new AnalysisResultResponse.NextAction(
                "VIEW_DIAGNOSTICS",
                "View diagnostics",
                true
        ));

        return actions;
    }

    private String extractFailureCode(Analysis analysis) {
        // Try to get from diagnostics or AiTask
        List<AiTask> aiTasks = aiTaskRepository.findByAnalysisIdOrderByCreatedAtDescIdDesc(analysis.getId());
        if (!aiTasks.isEmpty()) {
            AiTask latestTask = aiTasks.get(0);
            if (latestTask.getFailureCode() != null) {
                return latestTask.getFailureCode();
            }
        }
        return "ANALYSIS_FAILED";
    }

    private String extractFailureMessage(Analysis analysis) {
        List<AiTask> aiTasks = aiTaskRepository.findByAnalysisIdOrderByCreatedAtDescIdDesc(analysis.getId());
        if (!aiTasks.isEmpty()) {
            AiTask latestTask = aiTasks.get(0);
            if (latestTask.getFailureMessage() != null) {
                return latestTask.getFailureMessage();
            }
        }
        return "Analysis execution failed";
    }
}