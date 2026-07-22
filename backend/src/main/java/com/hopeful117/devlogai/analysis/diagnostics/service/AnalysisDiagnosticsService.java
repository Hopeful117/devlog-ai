package com.hopeful117.devlogai.analysis.diagnostics.service;

import com.hopeful117.devlogai.analysis.diagnostics.dto.AnalysisDiagnosticsResponse;
import com.hopeful117.devlogai.analysis.diagnostics.dto.CollectionWarningResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface AnalysisDiagnosticsService {
    AnalysisDiagnosticsResponse getDiagnostics(UUID analysisId);
    List<CollectionWarningResponse> getWarnings(UUID analysisId);
    Map<String, Object> getContext(UUID analysisId);
}
