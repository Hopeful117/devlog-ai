package com.hopeful117.devlogai.storycontextanalysis.controller;

import com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResult;
import com.hopeful117.devlogai.storycontextanalysis.service.StoryContextAnalysisQueryService;
import com.hopeful117.devlogai.storycontextanalysis.usecase.AnalyzeStoryContextUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class StoryContextAnalysisController {

    private final AnalyzeStoryContextUseCase analyzeStoryContextUseCase;
    private final StoryContextAnalysisQueryService storyContextAnalysisQueryService;

    @PostMapping("/api/v1/projects/{projectSlug}/stories/{storyId}/analyze-context")
    public ResponseEntity<AnalyzeContextResponse> analyzeContext(
            @PathVariable @NotBlank String projectSlug,
            @PathVariable @NotNull UUID storyId,
            @Valid @RequestBody(required = false) AnalyzeContextRequest request
    ) {
        List<String> files = request != null ? request.files() : List.of();
        Map<String, Object> guidance = request != null ? request.guidance() : null;

        UUID aiTaskId = analyzeStoryContextUseCase.execute(
                projectSlug, storyId, files, guidance
        );
        return ResponseEntity.accepted().body(new AnalyzeContextResponse(aiTaskId));
    }

    @GetMapping("/api/v1/ai/tasks/{aiTaskId}/story-context-analysis")
    public ResponseEntity<StoryContextAnalysisResult> getStoryContextAnalysis(
            @PathVariable @NotNull UUID aiTaskId
    ) {
        return storyContextAnalysisQueryService.findByAiTaskId(aiTaskId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    public record AnalyzeContextRequest(
            List<String> files,
            Map<String, Object> guidance
    ) {}

    public record AnalyzeContextResponse(UUID aiTaskId) {}
}