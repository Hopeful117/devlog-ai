package com.hopeful117.devlogai.storycontextanalysis.controller;

import com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResponse;
import com.hopeful117.devlogai.storycontextanalysis.service.StoryContextAnalysisQueryService;
import com.hopeful117.devlogai.storycontextanalysis.service.AuthorizedStoryContextSnapshotReader;
import com.hopeful117.devlogai.authorization.AuthenticatedPrincipalResolver;
import com.hopeful117.devlogai.authorization.UnauthenticatedPrincipalException;
import com.hopeful117.devlogai.storycontextanalysis.usecase.AnalyzeStoryContextUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
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
    private final AuthorizedStoryContextSnapshotReader snapshotReader;
    private final AuthenticatedPrincipalResolver principalResolver;

    @PostMapping({"/api/v1/projects/{projectSlug}/analyze-context", "/api/v1/projects/{projectSlug}/stories/{storyId}/analyze-context"})
    public ResponseEntity<AnalyzeContextResponse> analyzeContext(
            @PathVariable @NotBlank String projectSlug,
            @PathVariable(required = false) UUID storyId,
            @Valid @RequestBody(required = false) AnalyzeContextRequest request,
            HttpServletRequest servletRequest
    ) {
        var principal = principalResolver.resolve(servletRequest)
                .orElseThrow(UnauthenticatedPrincipalException::new);
        List<String> files = request != null ? request.files() : List.of();
        Map<String, Object> guidance = request != null ? request.guidance() : null;
        String intent = request == null || request.intent() == null || request.intent().isBlank()
                ? "engineering-story-context-analysis" : request.intent();

        UUID aiTaskId = analyzeStoryContextUseCase.execute(
                projectSlug, storyId, intent, files, guidance, null, principal, "chatgpt"
        );
        return ResponseEntity.accepted().body(new AnalyzeContextResponse(aiTaskId));
    }

    @GetMapping("/api/v1/ai/tasks/{aiTaskId}/story-context-analysis")
    public ResponseEntity<StoryContextAnalysisResponse> getStoryContextAnalysis(
            @PathVariable @NotNull UUID aiTaskId,
            HttpServletRequest servletRequest
    ) {
        var principal = principalResolver.resolve(servletRequest)
                .orElseThrow(UnauthenticatedPrincipalException::new);
        snapshotReader.authorize(principal, aiTaskId);
        return storyContextAnalysisQueryService.findResponseByAiTaskId(aiTaskId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    public record AnalyzeContextRequest(
            List<String> files,
            Map<String, Object> guidance,
            String intent
    ) {
        public AnalyzeContextRequest(List<String> files, Map<String, Object> guidance) {
            this(files, guidance, "engineering-story-context-analysis");
        }
    }

    public record AnalyzeContextResponse(UUID aiTaskId) {}
}
