package com.hopeful117.devlogai.ai.interactiontrace.service;

import com.hopeful117.devlogai.ai.interactiontrace.dto.AiInteractionTraceResponse;
import com.hopeful117.devlogai.ai.interactiontrace.repository.AiInteractionTraceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiInteractionTraceQueryService {
    private final AiInteractionTraceRepository repository;

    @Transactional(readOnly = true)
    public List<AiInteractionTraceResponse> byTask(UUID aiTaskId) {
        return repository.findByAiTaskIdOrderByAttemptAscIdAsc(aiTaskId).stream()
                .map(AiInteractionTraceResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AiInteractionTraceResponse> byAnalysis(UUID analysisId) {
        return repository.findByAnalysisIdOrderByAttemptAscIdAsc(analysisId).stream()
                .map(AiInteractionTraceResponse::from)
                .toList();
    }
}
