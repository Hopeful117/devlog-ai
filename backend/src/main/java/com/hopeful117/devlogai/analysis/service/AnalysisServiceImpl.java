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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AnalysisServiceImpl implements AnalysisService {
    private final AnalysisRepository analysisRepository;

    private final ProjectRepository projectRepository;

    private final AnalysisMapper analysisMapper;

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

        analysis.setProject(project);
        analysis.setStatus(AnalysisStatus.PENDING);

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
