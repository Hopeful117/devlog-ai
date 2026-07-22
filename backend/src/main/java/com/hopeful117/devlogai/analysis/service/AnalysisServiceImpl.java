package com.hopeful117.devlogai.analysis.service;

import com.hopeful117.devlogai.analysis.dto.request.CreateAnalysisRequest;
import com.hopeful117.devlogai.analysis.dto.response.AnalysisResponse;
import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.analysis.mapper.AnalysisMapper;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.shared.exception.ConflictException;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.intent.service.IntentCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;

@Service
@RequiredArgsConstructor
public class AnalysisServiceImpl implements AnalysisService {
    private final AnalysisRepository analysisRepository;

    private final ProjectRepository projectRepository;

    private final AnalysisMapper analysisMapper;
    private final IntentCatalog intentCatalog;
    private final ObjectMapper objectMapper;

    @Override
    public AnalysisResponse create(
            CreateAnalysisRequest request) {

        Project project =
                projectRepository.findById(request.getProjectId())
                        .orElseThrow(() ->
                                new EntityNotFoundException(
                                        "Project",
                                        request.getProjectId()
                                )
                        );

        Analysis analysis =
                analysisMapper.toEntity(request);
        IntentDefinition intent = intentCatalog.resolve(request.getIntentId());

        analysis.setProject(project);
        analysis.setIntentId(intent.id());
        analysis.setIntentVersion(intent.version());
        if (request.getUserGuidance() == null || request.getUserGuidance().isEmpty()) {
            analysis.setUserGuidance(null);
        } else {
            @SuppressWarnings("unchecked")
            Map<String, Object> guidance = objectMapper.convertValue(request.getUserGuidance(), Map.class);
            analysis.setUserGuidance(Collections.unmodifiableMap(new LinkedHashMap<>(guidance)));
        }
        analysis.setStatus(AnalysisStatus.PENDING);
        analysis.setStartedAt(null);
        analysis.setCompletedAt(null);

        Analysis saved =
                analysisRepository.save(analysis);

        return analysisMapper.toResponse(saved);
    }


    @Override
    public AnalysisResponse getById(UUID id) {

        Analysis analysis =
                analysisRepository.findById(id)
                        .orElseThrow(() ->
                                new EntityNotFoundException(
                                        "Analysis",
                                        id
                                )
                        );

        return analysisMapper.toResponse(analysis);
    }

    @Override
    @Transactional
    public AnalysisResponse start(UUID id) {
        Analysis analysis = findForTransition(id);
        if (analysis.getStatus() != AnalysisStatus.PENDING) {
            throw new ConflictException(
                    "Analysis cannot transition from %s to IN_PROGRESS"
                            .formatted(analysis.getStatus())
            );
        }

        analysis.setStatus(AnalysisStatus.IN_PROGRESS);
        analysis.setStartedAt(Instant.now());
        analysis.setCompletedAt(null);
        return analysisMapper.toResponse(analysisRepository.save(analysis));
    }

    @Override
    @Transactional
    public AnalysisResponse fail(UUID id) {
        Analysis analysis = findForTransition(id);
        if (analysis.getStatus() != AnalysisStatus.IN_PROGRESS) {
            throw new ConflictException(
                    "Analysis cannot transition from %s to FAILED"
                            .formatted(analysis.getStatus())
            );
        }

        analysis.setStatus(AnalysisStatus.FAILED);
        analysis.setCompletedAt(Instant.now());
        return analysisMapper.toResponse(analysisRepository.save(analysis));
    }

    private Analysis findForTransition(UUID id) {
        return analysisRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new EntityNotFoundException("Analysis", id));
    }


    @Override
    public List<AnalysisResponse> getByProject(
            UUID projectId) {

        return analysisRepository
                .findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream()
                .map(analysisMapper::toResponse)
                .toList();
    }


    @Override
    public List<AnalysisResponse> getByProjectAndType(
            UUID projectId,
            AnalysisType type) {

        return analysisRepository
                .findByProjectIdAndTypeOrderByCreatedAtDesc(
                        projectId,
                        type
                )
                .stream()
                .map(analysisMapper::toResponse)
                .toList();
    }


    @Override
    public List<AnalysisResponse> getByProjectAndStatus(
            UUID projectId,
            AnalysisStatus status) {

        return analysisRepository
                .findByProjectIdAndStatusOrderByCreatedAtDesc(
                        projectId,
                        status
                )
                .stream()
                .map(analysisMapper::toResponse)
                .toList();
    }

}
