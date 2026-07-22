package com.hopeful117.devlogai.insight.service;

import com.hopeful117.devlogai.insight.dto.response.InsightResponse;
import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import com.hopeful117.devlogai.insight.entity.InsightType;

import java.util.List;
import java.util.UUID;

public interface InsightService {
    InsightResponse getById(UUID id);

    List<InsightResponse> getByProject(UUID projectId);

    List<InsightResponse> getByAnalysis(UUID analysisId);

    List<InsightResponse> getByProjectAndType(
            UUID projectId,
            InsightType type
    );

    List<InsightResponse> getByProjectAndSeverity(
            UUID projectId,
            InsightSeverity severity
    );

    List<InsightResponse> getByProjectAndTypeAndSeverity(
            UUID projectId,
            InsightType type,
            InsightSeverity severity
    );
}
