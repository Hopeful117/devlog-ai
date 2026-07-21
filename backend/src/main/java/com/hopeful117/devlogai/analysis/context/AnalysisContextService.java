package com.hopeful117.devlogai.analysis.context;

import java.util.UUID;

public interface AnalysisContextService {

    AnalysisContext build(UUID analysisId);
}
