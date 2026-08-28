package com.hopeful117.devlogai.analysis.evidence.service;

import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import com.hopeful117.devlogai.ai.task.repository.AiTaskRepository;
import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.ProjectedSnapshot;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.TaskIdentity;
import com.hopeful117.devlogai.analysis.evidence.projection.HistoricalSelectedEvidenceSnapshotProjector;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiTaskSelectedEvidenceServiceImpl implements AiTaskSelectedEvidenceService {
    private static final Set<AiTaskStatus> TERMINAL_STATUSES =
            Set.of(AiTaskStatus.COMPLETED, AiTaskStatus.FAILED);

    private final AnalysisRepository analysisRepository;
    private final AiTaskRepository aiTaskRepository;
    private final HistoricalSelectedEvidenceSnapshotProjector projector;

    @Override
    @Transactional(readOnly = true)
    public AiTaskSelectedEvidenceResponse getSelectedEvidence(UUID analysisId) {
        Analysis analysis = analysisRepository.findWithProjectById(analysisId)
                .orElseThrow(() -> new EntityNotFoundException("Analysis", analysisId));
        UUID projectId = analysis.getProject().getId();

        AiTask task = aiTaskRepository
                .findFirstByAnalysisIdOrderByCreatedAtDescIdDesc(analysisId)
                .orElse(null);
        if (task == null) {
            return AiTaskSelectedEvidenceResponse.noAiTask(analysisId, projectId);
        }

        validateAssociation(task, analysisId, projectId);
        TaskIdentity taskIdentity = new TaskIdentity(
                task.getId(), task.getTaskType(), task.getStatus(), task.getCreatedAt());

        if (task.getSelectedKnowledgeSnapshot() == null) {
            return TERMINAL_STATUSES.contains(task.getStatus())
                    ? AiTaskSelectedEvidenceResponse.snapshotUnavailable(
                            analysisId, projectId, taskIdentity)
                    : AiTaskSelectedEvidenceResponse.snapshotPending(
                            analysisId, projectId, taskIdentity);
        }

        ProjectedSnapshot snapshot = projector.project(
                task.getId(),
                task.getSelectionVersion(),
                task.getSelectionDigest(),
                analysisId,
                projectId,
                task.getSelectedKnowledgeSnapshot()
        );
        return AiTaskSelectedEvidenceResponse.available(
                analysisId,
                projectId,
                taskIdentity,
                task.getSelectionVersion(),
                task.getSelectionDigest(),
                snapshot
        );
    }

    private void validateAssociation(AiTask task, UUID analysisId, UUID projectId) {
        Analysis taskAnalysis = task.getAnalysis();
        if (taskAnalysis == null || !analysisId.equals(taskAnalysis.getId())
                || taskAnalysis.getProject() == null
                || !projectId.equals(taskAnalysis.getProject().getId())) {
            throw new IllegalStateException(
                    "Selected evidence association failed task=%s path=task.analysis"
                            .formatted(task.getId()));
        }
    }
}
