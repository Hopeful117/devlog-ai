package com.hopeful117.devlogai.insight.service;

import com.hopeful117.devlogai.insight.dto.response.InsightResponse;
import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import com.hopeful117.devlogai.insight.entity.InsightType;
import com.hopeful117.devlogai.insight.mapper.InsightMapper;
import com.hopeful117.devlogai.insight.repository.InsightRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InsightServiceImpl implements InsightService{

    private final InsightRepository insightRepository;

    private final InsightMapper insightMapper;


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
