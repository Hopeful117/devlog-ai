package com.hopeful117.devlogai.ai.engine.service;

import com.hopeful117.devlogai.ai.engine.dto.*;
import com.hopeful117.devlogai.ai.engine.exception.InvalidAiTaskResultException;
import com.hopeful117.devlogai.ai.engine.exception.AiTaskResultConflictException;
import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import com.hopeful117.devlogai.ai.task.repository.AiTaskRepository;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.fact.entity.Fact;
import com.hopeful117.devlogai.fact.repository.FactRepository;
import com.hopeful117.devlogai.observation.entity.Observation;
import com.hopeful117.devlogai.observation.repository.ObservationRepository;
import com.hopeful117.devlogai.proposal.entity.ProposalStatus;
import com.hopeful117.devlogai.proposal.entity.ValidatableProposal;
import com.hopeful117.devlogai.proposal.repository.ValidatableProposalRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
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
    private final AnalysisRepository analysisRepository;

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

        if (isTerminal(task.getStatus())) {
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
            failTask(task, request);
            aiTaskRepository.save(task);
            finishAnalysis(task, AnalysisStatus.FAILED, request.completedAt());
            log.warn("AI task marked failed correlationId={} failureCode={}",
                    correlationId, request.error().code());
            return acknowledgement(task, false);
        }

        validateReferences(task, request.proposals());
        proposalRepository.saveAll(toProposals(task, request.proposals()));
        completeTask(task, request.completedAt());
        aiTaskRepository.save(task);
        finishAnalysis(task, AnalysisStatus.COMPLETED, request.completedAt());
        log.info("AI task completed correlationId={} proposalCount={}",
                correlationId, request.proposals().size());
        return acknowledgement(task, false);
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
}
