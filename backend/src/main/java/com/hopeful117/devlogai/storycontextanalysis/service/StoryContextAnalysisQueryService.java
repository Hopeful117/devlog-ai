package com.hopeful117.devlogai.storycontextanalysis.service;

import com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResult;
import com.hopeful117.devlogai.storycontextanalysis.entity.StoryContextAnalysis;
import com.hopeful117.devlogai.storycontextanalysis.repository.StoryContextAnalysisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StoryContextAnalysisQueryService {

    private final StoryContextAnalysisRepository storyContextAnalysisRepository;

    @Transactional(readOnly = true)
    public Optional<StoryContextAnalysisResult> findByAiTaskId(UUID aiTaskId) {
        return storyContextAnalysisRepository.findByAiTaskId(aiTaskId)
                .map(this::toResult);
    }

    private StoryContextAnalysisResult toResult(StoryContextAnalysis analysis) {
        @SuppressWarnings("unchecked")
        var snapshot = analysis.getAnalysisSnapshot();
        return convertSnapshotToResult(snapshot);
    }

    @SuppressWarnings("unchecked")
    private StoryContextAnalysisResult convertSnapshotToResult(java.util.Map<String, Object> snapshot) {
        var objectMapper = new tools.jackson.databind.ObjectMapper();
        return objectMapper.convertValue(snapshot, StoryContextAnalysisResult.class);
    }
}
