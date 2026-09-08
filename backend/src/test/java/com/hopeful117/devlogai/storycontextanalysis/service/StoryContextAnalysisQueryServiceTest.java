package com.hopeful117.devlogai.storycontextanalysis.service;

import com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResult;
import com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResponse;
import com.hopeful117.devlogai.storycontextanalysis.entity.StoryContextAnalysis;
import com.hopeful117.devlogai.storycontextanalysis.repository.StoryContextAnalysisRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
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

        StoryContextAnalysis analysis = StoryContextAnalysis.builder()
                .id(UUID.randomUUID())
                .aiTask(null)
                .story(null)
                .analysisSnapshot(baseSnapshot("Test summary"))
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

        StoryContextAnalysis analysis = StoryContextAnalysis.builder()
                .id(UUID.randomUUID())
                .aiTask(null)
                .story(null)
                .analysisSnapshot(baseSnapshot("Immutable check"))
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

    @Test
    void shouldReturnNullFreshnessForHistoricalAnalysis() {
        UUID aiTaskId = UUID.randomUUID();

        StoryContextAnalysis analysis = StoryContextAnalysis.builder()
                .id(UUID.randomUUID())
                .aiTask(null)
                .story(null)
                .analysisSnapshot(baseSnapshot("Historical"))
                .contextDigest("digest")
                .promptExecutionMetadata(Map.of())
                .contextFreshness(null)
                .createdAt(Instant.now())
                .build();

        when(storyContextAnalysisRepository.findByAiTaskId(aiTaskId))
                .thenReturn(Optional.of(analysis));

        StoryContextAnalysisResponse response =
                storyContextAnalysisQueryService.findResponseByAiTaskId(aiTaskId).orElseThrow();

        assertNotNull(response.analysis());
        assertNull(response.contextFreshness(),
                "Historical analysis must expose null freshness, not UNKNOWN");
        assertEquals("Historical", response.analysis().objectiveUnderstanding().summary());
    }

    @Test
    void shouldPreserveStaleFreshnessSnapshot() {
        UUID aiTaskId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();

        Map<String, Object> staleFreshness = Map.of(
                "status", "STALE",
                "repositoryRevision", "abc123",
                "contextRevision", "def456",
                "sources", List.of(Map.of(
                        "sourceId", sourceId.toString(),
                        "name", "git-main",
                        "status", "STALE",
                        "observedRevision", "abc123",
                        "contextRevision", "def456",
                        "checkedAt", "2026-09-08T10:00:00Z"
                ))
        );

        StoryContextAnalysis analysis = StoryContextAnalysis.builder()
                .id(UUID.randomUUID())
                .aiTask(null)
                .story(null)
                .analysisSnapshot(baseSnapshot("Stale analysis"))
                .contextDigest("digest")
                .promptExecutionMetadata(Map.of())
                .contextFreshness(staleFreshness)
                .createdAt(Instant.now())
                .build();

        when(storyContextAnalysisRepository.findByAiTaskId(aiTaskId))
                .thenReturn(Optional.of(analysis));

        StoryContextAnalysisResponse response =
                storyContextAnalysisQueryService.findResponseByAiTaskId(aiTaskId).orElseThrow();

        assertNotNull(response.contextFreshness());
        assertEquals("STALE", response.contextFreshness().get("status"));
        assertEquals("abc123", response.contextFreshness().get("repositoryRevision"));
        assertEquals("def456", response.contextFreshness().get("contextRevision"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sources = (List<Map<String, Object>>) response.contextFreshness().get("sources");
        assertNotNull(sources);
        assertEquals(1, sources.size());
        assertEquals("STALE", sources.get(0).get("status"));
        assertEquals("git-main", sources.get(0).get("name"));
        assertEquals("abc123", sources.get(0).get("observedRevision"));
        assertEquals("def456", sources.get(0).get("contextRevision"));
    }

    @Test
    void shouldPreservePartiallyFreshSnapshot() {
        UUID aiTaskId = UUID.randomUUID();

        Map<String, Object> partialFreshness = Map.of(
                "status", "PARTIALLY_FRESH",
                "repositoryRevision", "abc123",
                "contextRevision", "abc123",
                "sources", List.of(
                        Map.of(
                                "sourceId", UUID.randomUUID().toString(),
                                "name", "git-main",
                                "status", "CURRENT",
                                "observedRevision", "abc123",
                                "contextRevision", "abc123"
                        ),
                        Map.of(
                                "sourceId", UUID.randomUUID().toString(),
                                "name", "notion-docs",
                                "status", "STALE",
                                "observedRevision", "abc123",
                                "contextRevision", "99999"
                        )
                )
        );

        StoryContextAnalysis analysis = StoryContextAnalysis.builder()
                .id(UUID.randomUUID())
                .aiTask(null)
                .story(null)
                .analysisSnapshot(baseSnapshot("Partial"))
                .contextDigest("digest")
                .promptExecutionMetadata(Map.of())
                .contextFreshness(partialFreshness)
                .createdAt(Instant.now())
                .build();

        when(storyContextAnalysisRepository.findByAiTaskId(aiTaskId))
                .thenReturn(Optional.of(analysis));

        StoryContextAnalysisResponse response =
                storyContextAnalysisQueryService.findResponseByAiTaskId(aiTaskId).orElseThrow();

        assertNotNull(response.contextFreshness());
        assertEquals("PARTIALLY_FRESH", response.contextFreshness().get("status"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sources = (List<Map<String, Object>>) response.contextFreshness().get("sources");
        assertEquals(2, sources.size());
        assertEquals("CURRENT", sources.get(0).get("status"));
        assertEquals("STALE", sources.get(1).get("status"));
    }

    @Test
    void shouldPreserveCurrentFreshnessSnapshot() {
        UUID aiTaskId = UUID.randomUUID();

        Map<String, Object> currentFreshness = Map.of(
                "status", "CURRENT",
                "repositoryRevision", "abc123",
                "contextRevision", "abc123",
                "sources", List.of(Map.of(
                        "sourceId", UUID.randomUUID().toString(),
                        "name", "git-main",
                        "status", "CURRENT",
                        "observedRevision", "abc123",
                        "contextRevision", "abc123"
                ))
        );

        StoryContextAnalysis analysis = StoryContextAnalysis.builder()
                .id(UUID.randomUUID())
                .aiTask(null)
                .story(null)
                .analysisSnapshot(baseSnapshot("Current"))
                .contextDigest("digest")
                .promptExecutionMetadata(Map.of())
                .contextFreshness(currentFreshness)
                .createdAt(Instant.now())
                .build();

        when(storyContextAnalysisRepository.findByAiTaskId(aiTaskId))
                .thenReturn(Optional.of(analysis));

        StoryContextAnalysisResponse response =
                storyContextAnalysisQueryService.findResponseByAiTaskId(aiTaskId).orElseThrow();

        assertNotNull(response.contextFreshness());
        assertEquals("CURRENT", response.contextFreshness().get("status"));
        assertEquals("abc123", response.contextFreshness().get("repositoryRevision"));
    }

    @Test
    void shouldPreservePerSourceInformation() {
        UUID aiTaskId = UUID.randomUUID();
        UUID sourceId1 = UUID.randomUUID();
        UUID sourceId2 = UUID.randomUUID();

        Map<String, Object> freshness = Map.of(
                "status", "STALE",
                "repositoryRevision", "abc123",
                "contextRevision", "def456",
                "sources", List.of(
                        Map.of(
                                "sourceId", sourceId1.toString(),
                                "name", "git-main",
                                "status", "STALE",
                                "observedRevision", "abc123",
                                "contextRevision", "def456",
                                "checkedAt", "2026-09-08T10:00:00Z"
                        ),
                        Map.of(
                                "sourceId", sourceId2.toString(),
                                "name", "notion-docs",
                                "status", "STALE",
                                "observedRevision", "abc123",
                                "contextRevision", "ghi789",
                                "checkedAt", "2026-09-08T10:00:00Z"
                        )
                )
        );

        StoryContextAnalysis analysis = StoryContextAnalysis.builder()
                .id(UUID.randomUUID())
                .aiTask(null)
                .story(null)
                .analysisSnapshot(baseSnapshot("Per-source"))
                .contextDigest("digest")
                .promptExecutionMetadata(Map.of())
                .contextFreshness(freshness)
                .createdAt(Instant.now())
                .build();

        when(storyContextAnalysisRepository.findByAiTaskId(aiTaskId))
                .thenReturn(Optional.of(analysis));

        StoryContextAnalysisResponse response =
                storyContextAnalysisQueryService.findResponseByAiTaskId(aiTaskId).orElseThrow();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sources = (List<Map<String, Object>>) response.contextFreshness().get("sources");
        assertEquals(2, sources.size());
        assertNotEquals(
                sources.get(0).get("contextRevision"),
                sources.get(1).get("contextRevision"),
                "Per-source context revisions must differ to prove per-source preservation");
    }

    @Test
    void shouldReturnEmptyResponseByAiTaskIdWhenNotFound() {
        UUID aiTaskId = UUID.randomUUID();
        when(storyContextAnalysisRepository.findByAiTaskId(aiTaskId))
                .thenReturn(Optional.empty());

        Optional<StoryContextAnalysisResponse> result =
                storyContextAnalysisQueryService.findResponseByAiTaskId(aiTaskId);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnStalenessUnchangedAfterProjectBecomesCurrent() {
        UUID aiTaskId = UUID.randomUUID();

        Map<String, Object> staleFreshness = Map.of(
                "status", "STALE",
                "repositoryRevision", "old-rev",
                "contextRevision", "baseline-rev",
                "sources", List.of()
        );

        StoryContextAnalysis analysis = StoryContextAnalysis.builder()
                .id(UUID.randomUUID())
                .aiTask(null)
                .story(null)
                .analysisSnapshot(baseSnapshot("Historical stale"))
                .contextDigest("digest")
                .promptExecutionMetadata(Map.of())
                .contextFreshness(staleFreshness)
                .createdAt(Instant.now())
                .build();

        when(storyContextAnalysisRepository.findByAiTaskId(aiTaskId))
                .thenReturn(Optional.of(analysis));

        StoryContextAnalysisResponse response =
                storyContextAnalysisQueryService.findResponseByAiTaskId(aiTaskId).orElseThrow();

        assertEquals("STALE", response.contextFreshness().get("status"),
                "Persisted STALE must remain STALE regardless of current project state");
        assertEquals("old-rev", response.contextFreshness().get("repositoryRevision"),
                "Persisted repository revision must be historically stable");
    }

    private Map<String, Object> baseSnapshot(String summary) {
        Map<String, Object> snapshot = new java.util.HashMap<>();
        snapshot.put("objectiveUnderstanding", Map.of("summary", summary));
        snapshot.put("architectureFindings", List.of());
        snapshot.put("decisionFindings", List.of());
        snapshot.put("evidenceFindings", List.of());
        snapshot.put("historicalContext", List.of());
        snapshot.put("constraintFindings", List.of());
        snapshot.put("impactedComponentFindings", List.of());
        snapshot.put("uncertainties", List.of());
        snapshot.put("missingInformation", List.of());
        snapshot.put("implementationQuestions", List.of());
        snapshot.put("confidence", "HIGH");
        snapshot.put("provenance", Map.of());
        snapshot.put("outputClassification", Map.of("entries", List.of()));
        return snapshot;
    }
}
