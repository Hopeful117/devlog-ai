package com.hopeful117.devlogai.engineeringevent.execution;

import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.analysis.workflow.AnalysisWorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EngineeringEventExecutionService {
    private final EngineeringEventExecutionPreparationService preparation;
    private final EngineeringEventExecutionClaimService claims;
    private final AnalysisWorkflowService workflow;
    private final AnalysisRepository analyses;

    public EngineeringEventExecutionResponse execute(UUID projectId, EngineeringEventExecutionRequest request) {
        var prepared = preparation.prepare(projectId, request);
        EngineeringEventExecutionClaimService.Claim claim;
        try { claim = claims.claim(prepared); }
        catch (DataIntegrityViolationException race) { claim = claims.winner(prepared).orElseThrow(() -> race); }
        if (claim.created()) workflow.start(claim.analysis().getId());
        var current = analyses.findById(claim.analysis().getId()).orElseThrow();
        var scope = claim.scope();
        return new EngineeringEventExecutionResponse(
                EngineeringEventExecutionResponse.PROJECTION_VERSION, current.getId(), current.getStatus(),
                projectId, scope.getSource().getId(), scope.getBaseCommit(), scope.getTargetCommit(),
                scope.getComparisonPolicy(), scope.isMergeCommit(), current.getIntentId(),
                current.getIntentVersion(), claim.created()
                ? EngineeringEventExecutionResponse.Outcome.CREATED
                : EngineeringEventExecutionResponse.Outcome.REUSED);
    }
}
