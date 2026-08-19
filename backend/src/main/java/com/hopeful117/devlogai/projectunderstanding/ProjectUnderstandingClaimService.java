package com.hopeful117.devlogai.projectunderstanding;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.projectunderstanding.dto.ProjectUnderstandingOutcome;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.repository.SourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class ProjectUnderstandingClaimService {
    private static final EnumSet<AnalysisStatus> ACTIVE =
            EnumSet.of(AnalysisStatus.PENDING, AnalysisStatus.IN_PROGRESS);
    private final AnalysisRepository analysisRepository;
    private final ProjectRepository projectRepository;
    private final SourceRepository sourceRepository;
    private final ProjectUnderstandingExecutionKey executionKey;
    private final ObjectMapper objectMapper;

    @Transactional
    ProjectUnderstandingClaim claim(PreparedProjectUnderstanding prepared) {
        Project project = projectRepository.findById(prepared.projectId())
                .orElseThrow(() -> new EntityNotFoundException("Project", prepared.projectId()));
        Source source = sourceRepository.findByIdAndProject_IdAndActiveTrue(
                        prepared.sourceId(), prepared.projectId())
                .orElseThrow(() -> new EntityNotFoundException("Active project Source", prepared.sourceId()));
        String key = executionKey.compute(prepared.projectId(), prepared.sourceId(),
                prepared.targetRevision(), prepared.intent(), prepared.guidance());
        var existing = analysisRepository.findByUnderstandingExecutionKeyAndStatusIn(key, ACTIVE);
        if (existing.isPresent()) {
            return new ProjectUnderstandingClaim(existing.get(), ProjectUnderstandingOutcome.REUSED);
        }
        Map<String, Object> guidance = null;
        if (prepared.guidance() != null && !prepared.guidance().isEmpty()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> converted = objectMapper.convertValue(prepared.guidance(), Map.class);
            guidance = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(converted));
        }
        Analysis analysis = Analysis.builder()
                .project(project)
                .selectedSource(source)
                .selectedSourceSnapshot(prepared.sourceSnapshot())
                .understandingExecutionKey(key)
                .type(AnalysisType.ARCHITECTURE_REVIEW)
                .intentId(prepared.intent().id())
                .intentVersion(prepared.intent().version())
                .userGuidance(guidance)
                .status(AnalysisStatus.PENDING)
                .targetRevision(prepared.resolvedRevision())
                .build();
        return new ProjectUnderstandingClaim(analysisRepository.saveAndFlush(analysis),
                ProjectUnderstandingOutcome.CREATED);
    }

    @Transactional(readOnly = true)
    java.util.Optional<ProjectUnderstandingClaim> findWinner(PreparedProjectUnderstanding prepared) {
        String key = executionKey.compute(prepared.projectId(), prepared.sourceId(),
                prepared.targetRevision(), prepared.intent(), prepared.guidance());
        return analysisRepository.findByUnderstandingExecutionKeyAndStatusIn(key, ACTIVE)
                .map(winner -> new ProjectUnderstandingClaim(
                        winner, ProjectUnderstandingOutcome.REUSED));
    }

    @Transactional
    void failPending(UUID analysisId) {
        Analysis analysis = analysisRepository.findByIdForUpdate(analysisId)
                .orElseThrow(() -> new EntityNotFoundException("Analysis", analysisId));
        if (analysis.getStatus() == AnalysisStatus.PENDING) {
            analysis.setStatus(AnalysisStatus.FAILED);
            analysis.setCompletedAt(Instant.now());
            analysisRepository.save(analysis);
        }
    }
}
