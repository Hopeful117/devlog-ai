package com.hopeful117.devlogai.engineeringevent.execution;

import com.hopeful117.devlogai.analysis.entity.*;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.engineeringevent.*;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.source.repository.SourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.util.*;

@Service
@RequiredArgsConstructor
class EngineeringEventExecutionClaimService {
    private static final EnumSet<AnalysisStatus> ACTIVE =
            EnumSet.of(AnalysisStatus.PENDING, AnalysisStatus.IN_PROGRESS);
    private final AnalysisRepository analyses;
    private final AnalysisEvolutionScopeRepository scopes;
    private final ProjectRepository projects;
    private final SourceRepository sources;
    private final EngineeringEventExecutionKey keys;
    private final ObjectMapper mapper;

    @Transactional
    Claim claim(PreparedEngineeringEventExecution prepared) {
        String key = keys.compute(prepared);
        var existing = analyses.findByEvolutionExecutionKeyAndStatusIn(key, ACTIVE);
        if (existing.isPresent()) return new Claim(existing.get(), false,
                scopes.findById(existing.get().getId()).orElseThrow());
        var project = projects.findById(prepared.projectId())
                .orElseThrow(() -> new EntityNotFoundException("Project", prepared.projectId()));
        var source = sources.findByIdAndProject_IdAndActiveTrue(prepared.sourceId(), prepared.projectId())
                .orElseThrow(() -> new EntityNotFoundException("Active project Source", prepared.sourceId()));
        Map<String, Object> guidance = null;
        if (prepared.guidance() != null && !prepared.guidance().isEmpty()) {
            @SuppressWarnings("unchecked") Map<String, Object> converted =
                    mapper.convertValue(prepared.guidance(), Map.class);
            guidance = Collections.unmodifiableMap(new LinkedHashMap<>(converted));
        }
        Analysis analysis = analyses.saveAndFlush(Analysis.builder()
                .project(project).selectedSource(source).selectedSourceSnapshot(prepared.sourceSnapshot())
                .evolutionExecutionKey(key).type(AnalysisType.PROJECT_EVOLUTION)
                .intentId(prepared.intent().id()).intentVersion(prepared.intent().version())
                .userGuidance(guidance).status(AnalysisStatus.PENDING)
                .targetRevision(prepared.targetCommit()).build());
        AnalysisEvolutionScope scope = scopes.save(AnalysisEvolutionScope.builder()
                .analysisId(analysis.getId()).analysis(analysis).project(project).source(source)
                .contextVersion(AnalysisEvolutionScope.CONTEXT_VERSION)
                .comparisonPolicy(EvolutionComparisonPolicy.FIRST_PARENT)
                .baseCommit(prepared.baseCommit()).targetCommit(prepared.targetCommit())
                .targetCommittedAt(prepared.commitContext().committedAt())
                .mergeCommit(prepared.commitContext().mergeCommit()).build());
        return new Claim(analysis, true, scope);
    }

    @Transactional(readOnly = true)
    Optional<Claim> winner(PreparedEngineeringEventExecution prepared) {
        return analyses.findByEvolutionExecutionKeyAndStatusIn(keys.compute(prepared), ACTIVE)
                .map(value -> new Claim(value, false, scopes.findById(value.getId()).orElseThrow()));
    }
    record Claim(Analysis analysis, boolean created, AnalysisEvolutionScope scope) { }
}
