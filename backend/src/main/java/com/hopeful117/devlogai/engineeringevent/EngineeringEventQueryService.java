package com.hopeful117.devlogai.engineeringevent;

import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.shared.exception.InvalidParameterException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EngineeringEventQueryService {
    private final EngineeringEventRepository events;
    private final AnalysisEvolutionScopeRepository scopes;
    private final ProjectRepository projects;

    public EngineeringEventPageResponse byProject(UUID projectId, int page, int requestedSize) {
        if (!projects.existsById(projectId)) throw new EntityNotFoundException("Project", projectId);
        if (page < 0) throw new InvalidParameterException("page", page);
        int size = Math.clamp(requestedSize, 1, 50);
        var result = events.findByProjectIdOrderByOccurredAtDescTargetCommitDescIdAsc(
                projectId, PageRequest.of(page, size));
        Map<UUID, AnalysisEvolutionScope> scopeMap = scopes.findAllById(result.stream()
                .map(value -> value.getAnalysis().getId()).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(AnalysisEvolutionScope::getAnalysisId, Function.identity()));
        return new EngineeringEventPageResponse(EngineeringEventPageResponse.PROJECTION_VERSION,
                result.stream().map(value -> response(value, scopeMap.get(value.getAnalysis().getId()))).toList(),
                page, size, result.getTotalElements(), result.getTotalPages(),
                result.hasPrevious(), result.hasNext());
    }

    public EngineeringEventResponse get(UUID id) {
        EngineeringEvent event = events.findDetailedById(id)
                .orElseThrow(() -> new EntityNotFoundException("Engineering Event", id));
        return response(event, scopes.findById(event.getAnalysis().getId()).orElseThrow());
    }

    private EngineeringEventResponse response(EngineeringEvent value, AnalysisEvolutionScope scope) {
        var proposal = value.getProposal();
        return new EngineeringEventResponse(EngineeringEventResponse.PROJECTION_VERSION,
                value.getId(), value.getProject().getId(), value.getAnalysis().getId(),
                proposal.getId(), value.getValidation().getId(), value.getSource().getId(),
                value.getCategory(), value.getTitle(), value.getSummary(), value.getSignificance(),
                value.getBaseCommit(), value.getTargetCommit(), scope.getComparisonPolicy(),
                scope.isMergeCommit(), value.getOccurredAt(), value.getCreatedAt(), proposal.getConfidence(),
                safe(proposal.getSupportingFactIds()).stream().distinct().limit(20).toList(),
                safe(proposal.getSupportingObservationIds()).stream().distinct().limit(20).toList(),
                safe(proposal.getEvidenceReferences()).stream().distinct().limit(30).toList());
    }
    private <T> List<T> safe(List<T> values) { return values == null ? List.of() : values; }
}
