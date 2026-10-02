package com.hopeful117.devlogai.storycontextanalysis.usecase;

import com.hopeful117.devlogai.ai.task.dto.response.AiTaskResponse;
import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import com.hopeful117.devlogai.ai.task.service.AiTaskService;
import com.hopeful117.devlogai.storycontextanalysis.service.StoryContextAnalysisQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded, read-only application capability for a Story Context Agent run.
 * Context construction and AI submission remain owned by the existing use case.
 */
@Service
@RequiredArgsConstructor
public class DevLogStoryAgentService implements DevLogStoryAgent {

    private static final String STORY_CONTEXT_ANALYSIS_INTENT = "engineering-story-context-analysis";

    private final AnalyzeStoryContextUseCase analyzeStoryContextUseCase;
    private final AiTaskService aiTaskService;
    private final StoryContextAnalysisQueryService analysisQueryService;
    private final ObjectMapper objectMapper;

    @Override
    public DevLogStoryAgentExecution execute(DevLogStoryAgentRequest request) {
        Map<String, Object> guidance = withNaturalIntent(request);
        PreparedStoryContext prepared = analyzeStoryContextUseCase.prepare(
                request.projectSlug(),
                request.storyId(),
                STORY_CONTEXT_ANALYSIS_INTENT,
                request.files());
        var taskId = analyzeStoryContextUseCase.executePrepared(
                prepared,
                guidance,
                request.idempotencyKey());

        AiTaskResponse task = aiTaskService.getById(taskId);
        if (task.id() == null || !taskId.equals(task.id())) {
            throw new IllegalStateException("AI task identity changed while reading Story Context execution");
        }
        Map<String, Object> result = result(task);
        Map<String, Object> diagnostics = diagnostics(task);

        return new DevLogStoryAgentExecution(
                task.status(),
                task.id(),
                task.id(),
                result,
                diagnostics);
    }

    private Map<String, Object> withNaturalIntent(DevLogStoryAgentRequest request) {
        Map<String, Object> guidance = new LinkedHashMap<>(request.guidance());
        guidance.put("focus", request.intent());
        return guidance;
    }

    private Map<String, Object> result(AiTaskResponse task) {
        if (task.status() != AiTaskStatus.COMPLETED) {
            return Map.of();
        }

        return analysisQueryService.findResponseByAiTaskId(task.id())
                .map(response -> objectMapper.convertValue(response, Map.class))
                .map(value -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("analysis", value);
                    result.put("contextDigest", task.contextDigest());
                    result.put("projectionDigest", task.projectionDigest());
                    Map<String, Object> snapshot = task.contextSnapshot();
                    putIfPresent(result, snapshot, "scope");
                    putIfPresent(result, snapshot, "freshness");
                    putIfPresent(result, snapshot, "accounting");
                    return result;
                })
                .orElseThrow(() -> new IllegalStateException(
                        "Completed Story Context task has no persisted analysis result"));
    }

    private void putIfPresent(Map<String, Object> target, Map<String, Object> source, String key) {
        if (source != null && source.get(key) != null) {
            target.put(key, source.get(key));
        }
    }

    private Map<String, Object> diagnostics(AiTaskResponse task) {
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("status", task.status().name());
        if (task.failureCode() != null) {
            diagnostics.put("failureCode", task.failureCode());
        }
        if (task.failureMessage() != null) {
            diagnostics.put("failureMessage", task.failureMessage());
        }
        return diagnostics;
    }
}
