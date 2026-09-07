package com.hopeful117.devlogai.storycontextanalysis.controller;

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
@RequestMapping("/api/v1/projects/{projectSlug}/stories/{storyId}")
@RequiredArgsConstructor
public class StoryContextAnalysisController {

    private final AnalyzeStoryContextUseCase analyzeStoryContextUseCase;

    @PostMapping("/analyze-context")
    public ResponseEntity<Void> analyzeContext(
            @PathVariable @NotBlank String projectSlug,
            @PathVariable @NotNull UUID storyId,
            @Valid @RequestBody(required = false) AnalyzeContextRequest request
    ) {
        List<String> files = request != null ? request.files() : List.of();
        Map<String, Object> guidance = request != null ? request.guidance() : null;

        analyzeStoryContextUseCase.execute(
                projectSlug, storyId, files, guidance
        );
        return ResponseEntity.accepted().build();
    }

    public record AnalyzeContextRequest(
            List<String> files,
            Map<String, Object> guidance
    ) {}
}