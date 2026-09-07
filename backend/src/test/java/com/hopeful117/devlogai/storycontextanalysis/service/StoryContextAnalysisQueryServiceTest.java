package com.hopeful117.devlogai.storycontextanalysis.service;

import com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResult;
import com.hopeful117.devlogai.storycontextanalysis.entity.StoryContextAnalysis;
import com.hopeful117.devlogai.storycontextanalysis.repository.StoryContextAnalysisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoryContextAnalysisQueryServiceTest {

    @Mock
    private StoryContextAnalysisRepository storyContextAnalysisRepository;

    @InjectMocks
    private StoryContextAnalysisQueryService storyContextAnalysisQueryService;

    @Test
    void shouldReturnEmptyWhenAiTaskNotFound() {
        UUID aiTaskId = UUID.randomUUID();
        when(storyContextAnalysisRepository.findByAiTaskId(aiTaskId))
                .thenReturn(Optional.empty());

        Optional<StoryContextAnalysisResult> result =
                storyContextAnalysisQueryService.findByAiTaskId(aiTaskId);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnAnalysisWhenFound() {
        UUID aiTaskId = UUID.randomUUID();

        Map<String, Object> snapshot = new java.util.HashMap<>();
        snapshot.put("objectiveUnderstanding", Map.of("summary", "Test summary"));
        snapshot.put("architectureFindings", java.util.List.of());
        snapshot.put("decisionFindings", java.util.List.of());
        snapshot.put("evidenceFindings", java.util.List.of());
        snapshot.put("historicalContext", java.util.List.of());
        snapshot.put("constraintFindings", java.util.List.of());
        snapshot.put("impactedComponentFindings", java.util.List.of());
        snapshot.put("uncertainties", java.util.List.of());
        snapshot.put("missingInformation", java.util.List.of());
        snapshot.put("implementationQuestions", java.util.List.of());
        snapshot.put("confidence", "HIGH");
        snapshot.put("provenance", Map.of());
        snapshot.put("outputClassification", Map.of("entries", java.util.List.of()));

        StoryContextAnalysis analysis = StoryContextAnalysis.builder()
                .id(UUID.randomUUID())
                .aiTask(null)
                .story(null)
                .analysisSnapshot(snapshot)
                .contextDigest("digest")
                .promptExecutionMetadata(Map.of())
                .createdAt(Instant.now())
                .build();

        when(storyContextAnalysisRepository.findByAiTaskId(aiTaskId))
                .thenReturn(Optional.of(analysis));

        Optional<StoryContextAnalysisResult> result =
                storyContextAnalysisQueryService.findByAiTaskId(aiTaskId);

        assertTrue(result.isPresent());
        assertEquals("Test summary",
                result.get().objectiveUnderstanding().summary());
        assertEquals(StoryContextAnalysisResult.Confidence.HIGH,
                result.get().confidence());
    }

    @Test
    void shouldNotModifyAnalysisOnRetrieval() {
        UUID aiTaskId = UUID.randomUUID();

        Map<String, Object> snapshot = new java.util.HashMap<>();
        snapshot.put("objectiveUnderstanding", Map.of("summary", "Immutable check"));
        snapshot.put("architectureFindings", java.util.List.of());
        snapshot.put("decisionFindings", java.util.List.of());
        snapshot.put("evidenceFindings", java.util.List.of());
        snapshot.put("historicalContext", java.util.List.of());
        snapshot.put("constraintFindings", java.util.List.of());
        snapshot.put("impactedComponentFindings", java.util.List.of());
        snapshot.put("uncertainties", java.util.List.of());
        snapshot.put("missingInformation", java.util.List.of());
        snapshot.put("implementationQuestions", java.util.List.of());
        snapshot.put("confidence", "LOW");
        snapshot.put("provenance", Map.of());
        snapshot.put("outputClassification", Map.of("entries", java.util.List.of()));

        StoryContextAnalysis analysis = StoryContextAnalysis.builder()
                .id(UUID.randomUUID())
                .aiTask(null)
                .story(null)
                .analysisSnapshot(snapshot)
                .contextDigest("digest")
                .promptExecutionMetadata(Map.of())
                .createdAt(Instant.now())
                .build();

        when(storyContextAnalysisRepository.findByAiTaskId(aiTaskId))
                .thenReturn(Optional.of(analysis));

        storyContextAnalysisQueryService.findByAiTaskId(aiTaskId);
        storyContextAnalysisQueryService.findByAiTaskId(aiTaskId);

        assertEquals(Map.of("summary", "Immutable check"),
                analysis.getAnalysisSnapshot().get("objectiveUnderstanding"));
    }
}
