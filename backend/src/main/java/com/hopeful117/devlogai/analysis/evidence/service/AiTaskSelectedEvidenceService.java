package com.hopeful117.devlogai.analysis.evidence.service;

import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse;

import java.util.UUID;

public interface AiTaskSelectedEvidenceService {
    AiTaskSelectedEvidenceResponse getSelectedEvidence(UUID analysisId);
}
