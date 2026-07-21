package com.hopeful117.devlogai.insight.service;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.insight.dto.request.CreateInsightRequest;
import com.hopeful117.devlogai.insight.dto.response.InsightResponse;
import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import com.hopeful117.devlogai.insight.entity.InsightType;
import com.hopeful117.devlogai.insight.mapper.InsightMapper;
import com.hopeful117.devlogai.insight.repository.InsightRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InsightServiceImpl implements InsightService{

    private final InsightRepository insightRepository;

    private final ProjectRepository projectRepository;

    private final AnalysisRepository analysisRepository;

    private final InsightMapper insightMapper;

    @Override
    public InsightResponse create(
            CreateInsightRequest request) {

        Project project =
                projectRepository.findById(request.getProjectId())
                        .orElseThrow(() ->
                                new EntityNotFoundException(
                                        "Project",
                                        request.getProjectId()
                                )
                        );

        Analysis analysis =
                analysisRepository.findById(request.getAnalysisId())
                        .orElseThrow(() ->
                                new EntityNotFoundException(
                                        "Analysis",
                                        request.getAnalysisId()
                                )
                        );

        if (!analysis.getProject()
                .getId()
                .equals(project.getId())) {

            throw new IllegalArgumentException(
                    "Analysis does not belong to the specified project"
            );
        }

        Insight insight =
                insightMapper.toEntity(request);

        insight.setProject(project);
        insight.setAnalysis(analysis);

        Insight saved =
                insightRepository.save(insight);

        return insightMapper.toResponse(saved);
    }


    @Override
    public InsightResponse getById(UUID id) {

        Insight insight =
                insightRepository.findById(id)
                        .orElseThrow(() ->
                                new EntityNotFoundException(
                                        "Insight",
                                        id
                                )
                        );

        return insightMapper.toResponse(insight);
    }


    @Override
    public List<InsightResponse> getByProject(
            UUID projectId) {

        return insightRepository
                .findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream()
                .map(insightMapper::toResponse)
                .toList();
    }


    @Override
    public List<InsightResponse> getByAnalysis(
            UUID analysisId) {

        return insightRepository
                .findByAnalysisIdOrderByCreatedAtDesc(analysisId)
                .stream()
                .map(insightMapper::toResponse)
                .toList();
    }


    @Override
    public List<InsightResponse> getByProjectAndType(
            UUID projectId,
            InsightType type) {

        return insightRepository
                .findByProjectIdAndTypeOrderByCreatedAtDesc(
                        projectId,
                        type
                )
                .stream()
                .map(insightMapper::toResponse)
                .toList();
    }


    @Override
    public List<InsightResponse> getByProjectAndSeverity(
            UUID projectId,
            InsightSeverity severity) {

        return insightRepository
                .findByProjectIdAndSeverityOrderByCreatedAtDesc(
                        projectId,
                        severity
                )
                .stream()
                .map(insightMapper::toResponse)
                .toList();
    }


    @Override
    public List<InsightResponse> getByProjectAndTypeAndSeverity(
            UUID projectId,
            InsightType type,
            InsightSeverity severity) {

        return insightRepository
                .findByProjectIdAndTypeAndSeverityOrderByCreatedAtDesc(
                        projectId,
                        type,
                        severity
                )
                .stream()
                .map(insightMapper::toResponse)
                .toList();
    }

}
