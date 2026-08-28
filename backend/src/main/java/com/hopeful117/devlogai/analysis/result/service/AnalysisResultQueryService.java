package com.hopeful117.devlogai.analysis.result.service;

import com.hopeful117.devlogai.analysis.result.dto.AnalysisResultResponse;
import java.util.UUID;

public interface AnalysisResultQueryService {
    AnalysisResultResponse getResult(UUID analysisId);
}