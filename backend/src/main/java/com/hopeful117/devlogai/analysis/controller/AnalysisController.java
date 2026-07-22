package com.hopeful117.devlogai.analysis.controller;

import com.hopeful117.devlogai.analysis.dto.request.CreateAnalysisRequest;
import com.hopeful117.devlogai.analysis.dto.response.AnalysisResponse;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.analysis.service.AnalysisService;
import com.hopeful117.devlogai.analysis.workflow.AnalysisWorkflowService;
import com.hopeful117.devlogai.analysis.workflow.dto.AnalysisWorkflowResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analyses")
@RequiredArgsConstructor
public class AnalysisController {
    private final AnalysisService analysisService;
    private final AnalysisWorkflowService analysisWorkflowService;

    @PostMapping
    public ResponseEntity<AnalysisResponse> create(
            @Valid @RequestBody CreateAnalysisRequest request) {

        AnalysisResponse response =
                analysisService.create(request);

        URI location = URI.create(
                "/api/v1/analyses/" + response.id()
        );

        return ResponseEntity
                .created(location)
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AnalysisResponse> getById(
            @PathVariable UUID id) {

        return ResponseEntity.ok(
                analysisService.getById(id)
        );
    }

    @PostMapping("/{id}/workflow")
    public ResponseEntity<AnalysisWorkflowResult> startWorkflow(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(
                analysisWorkflowService.start(id)
        );
    }

    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<AnalysisResponse>> getByProject(
            @PathVariable UUID projectId) {

        return ResponseEntity.ok(
                analysisService.getByProject(projectId)
        );
    }

    @GetMapping("/project/{projectId}/type/{type}")
    public ResponseEntity<List<AnalysisResponse>> getByProjectAndType(
            @PathVariable UUID projectId,
            @PathVariable AnalysisType type) {

        return ResponseEntity.ok(
                analysisService.getByProjectAndType(
                        projectId,
                        type
                )
        );
    }

    @GetMapping("/project/{projectId}/status/{status}")
    public ResponseEntity<List<AnalysisResponse>> getByProjectAndStatus(
            @PathVariable UUID projectId,
            @PathVariable AnalysisStatus status) {

        return ResponseEntity.ok(
                analysisService.getByProjectAndStatus(
                        projectId,
                        status
                )
        );
    }
}
