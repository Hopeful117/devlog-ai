package com.hopeful117.devlogai.analysis.service;

import com.hopeful117.devlogai.analysis.dto.request.CreateAnalysisRequest;
import com.hopeful117.devlogai.analysis.dto.response.AnalysisResponse;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;

import java.util.List;
import java.util.UUID;

public interface AnalysisService {

    AnalysisResponse create(CreateAnalysisRequest request);

    AnalysisResponse getById(UUID id);

    List<AnalysisResponse> getByProject(UUID projectId);

    List<AnalysisResponse> getByProjectAndType(
            UUID projectId,
            AnalysisType type
    );

    List<AnalysisResponse> getByProjectAndStatus(
            UUID projectId,
            AnalysisStatus status
    );
}
