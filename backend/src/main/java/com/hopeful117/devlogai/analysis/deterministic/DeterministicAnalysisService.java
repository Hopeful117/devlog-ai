package com.hopeful117.devlogai.analysis.deterministic;

import java.util.UUID;

public interface DeterministicAnalysisService {

    DeterministicAnalysisResult analyze(UUID analysisId);
}
