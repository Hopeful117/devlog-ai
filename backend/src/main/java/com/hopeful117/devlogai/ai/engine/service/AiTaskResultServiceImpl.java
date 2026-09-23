package com.hopeful117.devlogai.ai.engine.service;

import tools.jackson.databind.ObjectMapper;
import com.hopeful117.devlogai.ai.engine.dto.*;
import com.hopeful117.devlogai.ai.engine.exception.InvalidAiTaskResultException;
import com.hopeful117.devlogai.ai.engine.exception.AiTaskResultConflictException;
import com.hopeful117.devlogai.ai.reference.AiReferenceResolver;
import com.hopeful117.devlogai.ai.reference.AiReferenceResolutionException;
import com.hopeful117.devlogai.ai.interactiontrace.service.AiInteractionTracePersistenceService;
import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import com.hopeful117.devlogai.ai.task.repository.AiTaskRepository;
import com.hopeful117.devlogai.analysis.communication.CommunicationDecision;
import com.hopeful117.devlogai.analysis.communication.CommunicationDecisionService;
import com.hopeful117.devlogai.analysis.communication.AnalysisCommunicationUseCase;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.fact.entity.Fact;
import com.hopeful117.devlogai.fact.repository.FactRepository;
import com.hopeful117.devlogai.insight.repository.InsightRepository;
import com.hopeful117.devlogai.observation.entity.Observation;
import com.hopeful117.devlogai.observation.repository.ObservationRepository;
import com.hopeful117.devlogai.proposal.entity.ProposalStatus;
import com.hopeful117.devlogai.proposal.entity.ValidatableProposal;
import com.hopeful117.devlogai.proposal.repository.ValidatableProposalRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.storycontextanalysis.usecase.AnalyzeStoryContextUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiTaskResultServiceImpl implements AiTaskResultService {

    private final AiTaskRepository aiTaskRepository;
    private final ValidatableProposalRepository proposalRepository;
    private final FactRepository factRepository;
    private final ObservationRepository observationRepository;
    private final InsightRepository insightRepository;
    private final AnalysisRepository analysisRepository;
    private final AiProposalContractValidator proposalContractValidator;
    private final ObjectMapper objectMapper;
    private final AnalyzeStoryContextUseCase analyzeStoryContextUseCase;
    private final CommunicationDecisionService communicationDecisionService;
    private final AnalysisCommunicationUseCase analysisCommunicationUseCase;
    private final AiInteractionTracePersistenceService interactionTracePersistenceService;

    @Override
    @Transactional
    public AiTaskResultAcknowledgement handle(
            UUID correlationId,
            AiTaskResultRequest request
    ) {
        log.info("Receiving AI task result correlationId={} status={} proposalCount={}",
                correlationId, request.status(), request.proposals().size());
        validateContract(correlationId, request);
        AiTask task = aiTaskRepository.findByCorrelationIdForUpdate(correlationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "AI task correlation", correlationId
                ));
        validateExternalJobId(task, request.externalJobId());
        rejectTypedPayloadForLegacyTask(task, request);
        persistInteractionTraces(task, request);

        if (isStoryContextAnalysisIntent(task)) {
            return handleStoryContextAnalysis(task, request);
        }

        if (isTerminal(task.getStatus())) {
            if (task.getStatus() == AiTaskStatus.COMPLETED
                    && isSynthesisIntent(task)
                    && task.getSynthesisSnapshot() == null) {
                throw new AiTaskResultConflictException(
                        "AI_TASK_INVALID_TERMINAL_RESULT",
                        task.getStatus(),
                        "Architecture Overview v2 completed without its mandatory synthesis."
                );
            }
            if (!task.getStatus().name().equals(request.status().name())) {
                throw new AiTaskResultConflictException(
                        "AI_TASK_TERMINAL_CONFLICT",
                        task.getStatus(),
                        "AI task already ended with a different terminal status."
                );
            }
            finishAnalysis(
                    task,
                    task.getStatus() == AiTaskStatus.COMPLETED
                            ? AnalysisStatus.COMPLETED : AnalysisStatus.FAILED,
                    task.getCompletedAt() == null ? request.completedAt() : task.getCompletedAt()
            );
            log.info("AI task result acknowledged as duplicate correlationId={} taskStatus={}",
                    correlationId, task.getStatus());
            return acknowledgement(task, true);
        }
        if (task.getStatus() == AiTaskStatus.CREATED) {
            throw new AiTaskResultConflictException(
                    "AI_TASK_NOT_READY",
                    task.getStatus(),
                    "AI task cannot receive a result yet."
            );
        }
        if (task.getStatus() != AiTaskStatus.SUBMITTED
                && task.getStatus() != AiTaskStatus.PROCESSING) {
            throw new AiTaskResultConflictException(
                    "AI_TASK_INVALID_STATE",
                    task.getStatus(),
                    "AI task cannot receive a result from its current status."
            );
        }

        if (request.status() == AiTaskResultStatus.FAILED) {
            if (request.promptExecution() != null) {
                applyPromptExecution(task, request.promptExecution());
            }
            failTask(task, request);
            aiTaskRepository.save(task);
            finishAnalysis(task, AnalysisStatus.FAILED, request.completedAt());
            log.warn("AI task marked failed correlationId={} failureCode={}",
                    correlationId, request.error().code());
            return acknowledgement(task, false);
        }

        List<AiProposalResult> proposals = isTypedArchitectureOverview(task)
                ? resolveTypedProposals(task, request.proposals()) : request.proposals();
        AnalysisSynthesisResult synthesis = isTypedArchitectureOverview(task)
                ? resolveTypedSynthesis(task, request.synthesis()) : request.synthesis();
        validateReferences(task, proposals);
        proposalContractValidator.validate(task, proposals);
        validateSynthesis(task, request, synthesis);
        applyPromptExecution(task, request.promptExecution());
        persistSynthesis(task, synthesis);
        proposalRepository.saveAll(toProposals(task, proposals));
        completeTask(task, request.completedAt());
        aiTaskRepository.save(task);
        finishAnalysis(task, AnalysisStatus.COMPLETED, request.completedAt());
        log.info("AI task completed correlationId={} proposalCount={} hasSynthesis={}",
                correlationId, request.proposals().size(), request.synthesis() != null);
        evaluateAndCommunicate(task.getAnalysis().getId());
        return acknowledgement(task, false);
    }

    private void persistInteractionTraces(AiTask task, AiTaskResultRequest request) {
        try {
            interactionTracePersistenceService.persist(task, request.interactionTraces());
        } catch (RuntimeException traceFailure) {
            log.warn("AI interaction trace persistence failed taskId={} traceCount={}",
                    task.getId(), request.interactionTraces().size(), traceFailure);
        }
    }

    private void finishAnalysis(AiTask task, AnalysisStatus status, Instant completedAt) {
        task.getAnalysis().setStatus(status);
        task.getAnalysis().setCompletedAt(completedAt);
        analysisRepository.save(task.getAnalysis());
    }

    private void validateContract(
            UUID correlationId,
            AiTaskResultRequest request
    ) {
        if (!correlationId.equals(request.correlationId())) {
            throw new InvalidAiTaskResultException(
                    "Path and payload correlation identifiers must match"
            );
        }
        if (request.status() == AiTaskResultStatus.COMPLETED
                && request.error() != null) {
            throw new InvalidAiTaskResultException(
                    "A completed result must not contain an error"
            );
        }
        if (request.status() == AiTaskResultStatus.COMPLETED
                && request.promptExecution() == null) {
            throw new InvalidAiTaskResultException(
                    "A completed result must contain Prompt execution metadata"
            );
        }
        if (request.status() == AiTaskResultStatus.FAILED) {
            if (request.error() == null) {
                throw new InvalidAiTaskResultException(
                        "A failed result must contain an error"
                );
            }
            if (!request.proposals().isEmpty()) {
                throw new InvalidAiTaskResultException(
                        "A failed result must not contain proposals"
                );
            }
        }
    }

    private void validateExternalJobId(AiTask task, String externalJobId) {
        if (task.getExternalJobId() != null
                && !task.getExternalJobId().equals(externalJobId)) {
            throw new InvalidAiTaskResultException(
                    "Callback external job identifier does not match the AI task"
            );
        }
    }

    private boolean isTerminal(AiTaskStatus status) {
        return status == AiTaskStatus.COMPLETED || status == AiTaskStatus.FAILED;
    }

    private void applyPromptExecution(AiTask task, PromptExecutionMetadata metadata) {
        task.setPromptVersion(metadata.promptVersion());
        task.setProvider(metadata.provider());
        task.setModelIdentifier(metadata.modelIdentifier());
        task.setPromptContentDigest(metadata.promptContentDigest());
        task.setContextDigest(metadata.contextDigest());
        log.info("Persisting Prompt metadata taskId={} promptVersion={} promptDigest={} provider={} model={}",
                task.getId(), metadata.promptVersion(), metadata.promptContentDigest(),
                metadata.provider(), metadata.modelIdentifier());
    }

    @SuppressWarnings("unchecked")
    private void persistSynthesis(AiTask task, AnalysisSynthesisResult synthesis) {
        if (synthesis == null) {
            return;
        }
        Map<String, Object> snapshot = objectMapper.convertValue(synthesis, Map.class);
        task.setSynthesisSnapshot(snapshot);
        log.info("Persisting synthesis snapshot taskId={} title={}", task.getId(), synthesis.title());
    }

    private void validateSynthesis(AiTask task, AiTaskResultRequest request,
            AnalysisSynthesisResult synthesis) {
        if (request.status() != AiTaskResultStatus.COMPLETED) {
            return;
        }
        boolean synthesisIntent = isSynthesisIntent(task);
        if (synthesisIntent && synthesis == null) {
            throw new InvalidAiTaskResultException(
                    "Architecture Overview v2 callback must include synthesis"
            );
        }
        if (!synthesisIntent && synthesis != null) {
            throw new InvalidAiTaskResultException(
                    "Synthesis is not allowed for this Intent version"
            );
        }
        if (synthesis != null) {
            if (synthesis.title() == null || synthesis.title().isBlank()) {
                throw new InvalidAiTaskResultException("Synthesis title must not be blank");
            }
            if (synthesis.sections() == null || synthesis.sections().isEmpty()) {
                throw new InvalidAiTaskResultException("Synthesis must have at least one section");
            }
            boolean hasDeltas = !request.proposals().isEmpty();
            boolean declaresDeltas = synthesis.deltaConclusion()
                    == AnalysisSynthesisResult.ArchitectureDeltaConclusion.DELTAS_PROPOSED;
            if (hasDeltas != declaresDeltas) {
                throw new InvalidAiTaskResultException(
                        "Synthesis delta conclusion must match whether proposals are present"
                );
            }
            proposalContractValidator.validateSynthesis(task, synthesis, hasDeltas);
        }
    }

    private boolean isSynthesisIntent(AiTask task) {
        return "architecture-overview".equals(task.getIntentId())
                && Set.of("v2", "v3").contains(task.getIntentVersion());
    }

    private boolean isTypedArchitectureOverview(AiTask task) {
        return "architecture-overview".equals(task.getIntentId())
                && "v3".equals(task.getIntentVersion());
    }

    private void rejectTypedPayloadForLegacyTask(AiTask task, AiTaskResultRequest request) {
        if (isTypedArchitectureOverview(task)) return;
        boolean typedProposal = request.proposals().stream().anyMatch(proposal ->
                proposal.supportingFactRefs() != null
                        || proposal.supportingObservationRefs() != null
                        || proposal.evidenceRefs() != null);
        boolean typedSynthesis = request.synthesis() != null
                && request.synthesis().groundingRefs() != null;
        if (typedProposal || typedSynthesis) {
            throw new AiReferenceResolutionException("INCOMPATIBLE_TYPED_CONTRACT",
                    "Typed references are not accepted for a legacy Intent version");
        }
    }

    private List<AiProposalResult> resolveTypedProposals(AiTask task,
            List<AiProposalResult> proposals) {
        var resolver = typedResolver(task);
        return proposals.stream().map(proposal -> {
            if (proposal.supportingFactRefs() == null
                    || proposal.supportingObservationRefs() == null
                    || proposal.evidenceRefs() == null
                    || !proposal.supportingFactIds().isEmpty()
                    || !proposal.supportingObservationIds().isEmpty()
                    || !proposal.evidenceReferences().isEmpty()) {
                throw referenceFailure("MALFORMED_REFERENCE",
                        "Architecture Overview v3 requires typed grounding fields only");
            }
            List<UUID> facts = proposal.supportingFactRefs().stream()
                    .map(reference -> resolveDomainUuid(resolver, reference, "SUPPORTING_FACT"))
                    .toList();
            List<UUID> observations = proposal.supportingObservationRefs().stream()
                    .map(reference -> resolveDomainUuid(resolver, reference, "SUPPORTING_OBSERVATION"))
                    .toList();
            List<String> evidence = proposal.evidenceRefs().stream()
                    .map(reference -> resolver.resolve(toCore(reference), "EVIDENCE_REFERENCE")
                            .canonicalSourceIdentity())
                    .toList();
            Map<String, Object> payload = new LinkedHashMap<>(proposal.payload());
            Object target = payload.remove("targetInsightRef");
            if (target == null && "ENRICHES".equals(payload.get("deltaType"))) {
                throw referenceFailure("MALFORMED_REFERENCE",
                        "Architecture Insight ENRICHES requires targetInsightRef");
            }
            if (target != null && !"ENRICHES".equals(payload.get("deltaType"))) {
                throw referenceFailure("MALFORMED_REFERENCE",
                        "Architecture Insight NEW must omit targetInsightRef");
            }
            if (target != null && !(target instanceof Map<?, ?>)) {
                throw referenceFailure("MALFORMED_REFERENCE",
                        "Architecture target reference is malformed");
            }
            if (target instanceof Map<?, ?> targetMap) {
                var targetReference = toCore(targetMap);
                if (targetReference.type() != com.hopeful117.devlogai.ai.reference.AiReferenceType.INSIGHT
                        || targetReference.scope() != com.hopeful117.devlogai.ai.reference.AiReferenceScope.PROJECT) {
                    throw referenceFailure("REFERENCE_NAMESPACE_MISMATCH",
                            "Architecture target must be a project Insight reference");
                }
                String targetUuid = resolver.resolveArchitectureTarget(targetReference)
                        .canonicalSourceIdentity();
                UUID targetId;
                try {
                    targetId = UUID.fromString(targetUuid);
                } catch (IllegalArgumentException exception) {
                    throw referenceFailure("REFERENCE_MAPPING_FAILURE",
                            "Architecture target does not map to a UUID domain identity");
                }
                var targetInsight = insightRepository.findById(targetId).orElseThrow(() ->
                        referenceFailure("REFERENCE_MAPPING_FAILURE",
                                "Architecture target Insight is no longer available"));
                if (task.getAnalysis() == null || task.getAnalysis().getProject() == null
                        || targetInsight.getProject() == null
                        || !task.getAnalysis().getProject().getId().equals(targetInsight.getProject().getId())) {
                    throw referenceFailure("REFERENCE_MAPPING_FAILURE",
                            "Architecture target Insight is outside the task project");
                }
                payload.put("targetInsightId", targetId.toString());
            }
            return new AiProposalResult(proposal.type(), payload, proposal.confidence(),
                    facts, observations, evidence, proposal.supportingFactRefs(),
                    proposal.supportingObservationRefs(), proposal.evidenceRefs());
        }).toList();
    }

    private AnalysisSynthesisResult resolveTypedSynthesis(AiTask task,
            AnalysisSynthesisResult synthesis) {
        if (synthesis == null) return null;
        if (synthesis.groundingRefs() == null || !synthesis.groundingReferences().isEmpty()) {
            throw referenceFailure("MALFORMED_REFERENCE",
                    "Architecture Overview v3 synthesis requires groundingRefs only");
        }
        var resolver = typedResolver(task);
        List<String> resolved = synthesis.groundingRefs().stream()
                .map(reference -> {
                    var coreReference = toCore(reference);
                    String capability = switch (coreReference.type()) {
                        case FACT -> "SUPPORTING_FACT";
                        case OBSERVATION -> "SUPPORTING_OBSERVATION";
                        case REPOSITORY_EVIDENCE -> "EVIDENCE_REFERENCE";
                        default -> throw referenceFailure("REFERENCE_NOT_ALLOWED_FOR_GROUNDING",
                                "Synthesis reference is not a grounding candidate");
                    };
                    return resolver.resolve(coreReference, capability).canonicalSourceIdentity();
                })
                .toList();
        return new AnalysisSynthesisResult(synthesis.title(), synthesis.sections(),
                synthesis.deltaConclusion(), resolved, synthesis.groundingRefs());
    }

    private AiReferenceResolver typedResolver(AiTask task) {
        if (task.getAiReferenceMappingSnapshot() == null) {
            throw referenceFailure("REFERENCE_MAPPING_FAILURE",
                    "Architecture Overview v3 requires an AI reference mapping snapshot");
        }
        return AiReferenceResolver.fromMap(task.getAiReferenceMappingSnapshot());
    }

    private UUID resolveDomainUuid(AiReferenceResolver resolver, ProviderAiReference reference,
            String capability) {
        String identity = resolver.resolve(toCore(reference), capability).canonicalSourceIdentity();
        try {
            return UUID.fromString(identity);
        } catch (IllegalArgumentException exception) {
            throw referenceFailure("REFERENCE_MAPPING_FAILURE",
                    "Typed reference does not map to a UUID domain identity");
        }
    }

    private com.hopeful117.devlogai.ai.reference.AiReference toCore(ProviderAiReference reference) {
        if (reference == null || reference.type() == null || reference.ref() == null
                || reference.ref().isBlank() || reference.scope() == null) {
            throw referenceFailure("MALFORMED_REFERENCE", "Typed AI reference is malformed");
        }
        return new com.hopeful117.devlogai.ai.reference.AiReference(reference.type(),
                reference.ref(), reference.scope());
    }

    private com.hopeful117.devlogai.ai.reference.AiReference toCore(Map<?, ?> reference) {
        try {
            return new com.hopeful117.devlogai.ai.reference.AiReference(
                    com.hopeful117.devlogai.ai.reference.AiReferenceType.valueOf(
                            String.valueOf(reference.get("type"))),
                    String.valueOf(reference.get("ref")),
                    com.hopeful117.devlogai.ai.reference.AiReferenceScope.valueOf(
                            String.valueOf(reference.get("scope"))));
        } catch (RuntimeException exception) {
            throw referenceFailure("MALFORMED_REFERENCE", "Typed AI reference is malformed");
        }
    }

    private AiReferenceResolutionException referenceFailure(String code, String message) {
        return new AiReferenceResolutionException(code, message);
    }

    private void validateReferences(
            AiTask task,
            List<AiProposalResult> proposals
    ) {
        UUID analysisId = task.getAnalysis().getId();
        Set<UUID> factIds = new LinkedHashSet<>();
        Set<UUID> observationIds = new LinkedHashSet<>();
        proposals.forEach(proposal -> {
            factIds.addAll(proposal.supportingFactIds());
            observationIds.addAll(proposal.supportingObservationIds());
        });

        List<Fact> facts = factRepository.findAllById(factIds);
        if (facts.size() != factIds.size()
                || facts.stream().anyMatch(fact ->
                !analysisId.equals(fact.getAnalysis().getId()))) {
            throw new InvalidAiTaskResultException(
                    "Supporting facts must exist and belong to the task analysis"
            );
        }

        List<Observation> observations = observationRepository.findAllById(
                observationIds
        );
        if (observations.size() != observationIds.size()
                || observations.stream().anyMatch(observation ->
                !analysisId.equals(observation.getAnalysis().getId()))) {
            throw new InvalidAiTaskResultException(
                    "Supporting observations must exist and belong to the task analysis"
            );
        }
    }

    private List<ValidatableProposal> toProposals(
            AiTask task,
            List<AiProposalResult> results
    ) {
        List<ValidatableProposal> proposals = new ArrayList<>(results.size());
        for (int index = 0; index < results.size(); index++) {
            AiProposalResult result = results.get(index);
            proposals.add(ValidatableProposal.builder()
                    .project(task.getAnalysis().getProject())
                    .analysis(task.getAnalysis())
                    .aiTask(task)
                    .sourceIndex(index)
                    .type(result.type())
                    .status(ProposalStatus.PROPOSED)
                    .payload(result.payload())
                    .confidence(result.confidence())
                    .supportingFactIds(List.copyOf(result.supportingFactIds()))
                    .supportingObservationIds(
                            List.copyOf(result.supportingObservationIds())
                    )
                    .evidenceReferences(List.copyOf(result.evidenceReferences()))
                    .build());
        }
        return proposals;
    }

    private void completeTask(AiTask task, Instant completedAt) {
        if (task.getStatus() == AiTaskStatus.SUBMITTED) {
            task.setStartedAt(completedAt);
        }
        task.setStatus(AiTaskStatus.COMPLETED);
        task.setCompletedAt(completedAt);
        task.setFailureCode(null);
        task.setFailureMessage(null);
    }

    private void failTask(AiTask task, AiTaskResultRequest request) {
        if (task.getStatus() == AiTaskStatus.SUBMITTED) {
            task.setStartedAt(request.completedAt());
        }
        task.setStatus(AiTaskStatus.FAILED);
        task.setFailureCode(request.error().code());
        task.setFailureMessage(request.error().message());
        task.setCompletedAt(request.completedAt());
    }

    private AiTaskResultAcknowledgement acknowledgement(
            AiTask task,
            boolean duplicate
    ) {
        return new AiTaskResultAcknowledgement(
                task.getCorrelationId(),
                true,
                duplicate,
                task.getStatus(),
                proposalRepository.countByAiTaskId(task.getId())
        );
    }

    private boolean isStoryContextAnalysisIntent(AiTask task) {
        return "engineering-story-context-analysis".equals(task.getIntentId())
                && "v1".equals(task.getIntentVersion());
    }

    private AiTaskResultAcknowledgement handleStoryContextAnalysis(AiTask task, AiTaskResultRequest request) {
        if (task.getStatus().isTerminal()) {
            log.info("Duplicate callback for completed story context analysis task correlationId={}", task.getCorrelationId());
            return acknowledgement(task, true);
        }
        if (task.getStatus() == AiTaskStatus.CREATED) {
            throw new AiTaskResultConflictException(
                    "AI_TASK_NOT_READY",
                    task.getStatus(),
                    "AI task cannot receive a result yet."
            );
        }
        if (task.getStatus() != AiTaskStatus.SUBMITTED
                && task.getStatus() != AiTaskStatus.PROCESSING) {
            throw new AiTaskResultConflictException(
                    "AI_TASK_INVALID_STATE",
                    task.getStatus(),
                    "AI task cannot receive a result from its current status."
            );
        }

        if (request.status() == AiTaskResultStatus.FAILED) {
            if (request.promptExecution() != null) {
                applyPromptExecution(task, request.promptExecution());
            }
            failTask(task, request);
            aiTaskRepository.save(task);
            finishAnalysis(task, AnalysisStatus.FAILED, request.completedAt());
            log.warn("Story context analysis task marked failed correlationId={} failureCode={}",
                    task.getCorrelationId(), request.error().code());
            analyzeStoryContextUseCase.handleCallback(task.getCorrelationId(), request);
            return acknowledgement(task, false);
        }

        if (request.analysisResult() == null) {
            throw new InvalidAiTaskResultException(
                    "Story Context Analysis callback must include analysisResult"
            );
        }

        analyzeStoryContextUseCase.handleCallback(task.getCorrelationId(), request);
        finishAnalysis(task, AnalysisStatus.COMPLETED, request.completedAt());
        evaluateAndCommunicate(task.getAnalysis().getId());

        return acknowledgement(task, false);
    }

    private void evaluateAndCommunicate(UUID analysisId) {
        try {
            CommunicationDecision decision = communicationDecisionService.evaluate(analysisId);
            if (decision == CommunicationDecision.SPEAK) {
                analysisCommunicationUseCase.execute(analysisId);
                log.info("Autonomous communication sent for analysis {}", analysisId);
            } else {
                log.debug("Autonomous communication skipped for analysis {} (SILENCE)", analysisId);
            }
        } catch (Exception e) {
            log.warn("Autonomous communication failed for analysis {}; analysis completion unaffected: {}",
                    analysisId, e.getMessage());
        }
    }
}
