package com.hopeful117.devlogai.projectunderstanding;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.analysis.workflow.AnalysisWorkflowService;
import com.hopeful117.devlogai.projectunderstanding.dto.ProjectUnderstandingOutcome;
import com.hopeful117.devlogai.projectunderstanding.dto.ProjectUnderstandingRequest;
import com.hopeful117.devlogai.projectunderstanding.dto.ProjectUnderstandingResponse;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectUnderstandingService {
    private final ProjectUnderstandingPreparationService preparationService;
    private final ProjectUnderstandingClaimService claimService;
    private final AnalysisWorkflowService workflowService;
    private final AnalysisRepository analysisRepository;

    public ProjectUnderstandingResponse execute(UUID projectId, ProjectUnderstandingRequest request) {
        PreparedProjectUnderstanding prepared = preparationService.prepare(projectId, request);
        ProjectUnderstandingClaim claim;
        try {
            claim = claimService.claim(prepared);
        } catch (DataIntegrityViolationException race) {
            claim = claimService.findWinner(prepared).orElseThrow(() -> race);
        }
        if (claim.outcome() == ProjectUnderstandingOutcome.REUSED) {
            return response(claim.analysis(), claim.outcome());
        }
        try {
            workflowService.start(claim.analysis().getId());
        } catch (RuntimeException failure) {
            claimService.failPending(claim.analysis().getId());
            throw failure;
        }
        UUID analysisId = claim.analysis().getId();
        Analysis current = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new EntityNotFoundException("Analysis", analysisId));
        return response(current, claim.outcome());
    }

    private ProjectUnderstandingResponse response(Analysis analysis,
                                                   ProjectUnderstandingOutcome outcome) {
        UUID sourceId = analysis.getSelectedSource() == null ? snapshotSourceId(analysis)
                : analysis.getSelectedSource().getId();
        return new ProjectUnderstandingResponse(analysis.getId(), analysis.getStatus(), sourceId,
                analysis.getTargetRevision(), analysis.getIntentId(), analysis.getIntentVersion(),
                outcome, analysis.getSelectedSourceSnapshot());
    }

    private UUID snapshotSourceId(Analysis analysis) {
        Object value = analysis.getSelectedSourceSnapshot().get("id");
        return UUID.fromString(String.valueOf(value));
    }
}
