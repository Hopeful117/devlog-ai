package com.hopeful117.devlogai.history.controller;

import com.hopeful117.devlogai.history.context.CommitDiffAnalysisContext;
import com.hopeful117.devlogai.history.dto.HistoryImportResult;
import com.hopeful117.devlogai.history.dto.ProjectCommitResponse;
import com.hopeful117.devlogai.history.service.ProjectHistoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/project-history")
public class ProjectHistoryController {
    private final ProjectHistoryService historyService;

    public ProjectHistoryController(ProjectHistoryService historyService) {
        this.historyService = historyService;
    }

    @PostMapping("/repositories/{repositoryId}/imports")
    public ResponseEntity<HistoryImportResult> importHistory(
            @PathVariable UUID repositoryId,
            @RequestParam(required = false) String revision
    ) {
        return ResponseEntity.ok(historyService.importHistory(repositoryId, revision));
    }

    @GetMapping("/projects/{projectId}/commits")
    public ResponseEntity<List<ProjectCommitResponse>> getProjectHistory(
            @PathVariable UUID projectId
    ) {
        return ResponseEntity.ok(historyService.getProjectHistory(projectId));
    }

    @GetMapping("/repositories/{repositoryId}/commits/{commitHash}/context")
    public ResponseEntity<CommitDiffAnalysisContext> getCommitContext(
            @PathVariable UUID repositoryId,
            @PathVariable String commitHash
    ) {
        return ResponseEntity.ok(historyService.getCommitContext(repositoryId, commitHash));
    }
}
