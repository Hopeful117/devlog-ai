package com.hopeful117.devlogai.insight.controller;

import com.hopeful117.devlogai.insight.dto.response.InsightResponse;
import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import com.hopeful117.devlogai.insight.entity.InsightType;
import com.hopeful117.devlogai.insight.service.InsightService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/insights")
public class InsightController {

    private final InsightService insightService;

    @GetMapping("/{id}")
    public ResponseEntity<InsightResponse> getById(
            @PathVariable UUID id) {

        return ResponseEntity.ok(
                insightService.getById(id)
        );
    }

    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<InsightResponse>> getByProject(
            @PathVariable UUID projectId) {

        return ResponseEntity.ok(
                insightService.getByProject(projectId)
        );
    }

    @GetMapping("/analysis/{analysisId}")
    public ResponseEntity<List<InsightResponse>> getByAnalysis(
            @PathVariable UUID analysisId) {

        return ResponseEntity.ok(
                insightService.getByAnalysis(analysisId)
        );
    }

    @GetMapping("/project/{projectId}/type/{type}")
    public ResponseEntity<List<InsightResponse>> getByProjectAndType(
            @PathVariable UUID projectId,
            @PathVariable InsightType type) {

        return ResponseEntity.ok(
                insightService.getByProjectAndType(
                        projectId,
                        type
                )
        );
    }

    @GetMapping("/project/{projectId}/severity/{severity}")
    public ResponseEntity<List<InsightResponse>> getByProjectAndSeverity(
            @PathVariable UUID projectId,
            @PathVariable InsightSeverity severity) {

        return ResponseEntity.ok(
                insightService.getByProjectAndSeverity(
                        projectId,
                        severity
                )
        );
    }

    @GetMapping(
            "/project/{projectId}/type/{type}/severity/{severity}"
    )
    public ResponseEntity<List<InsightResponse>>
    getByProjectAndTypeAndSeverity(
            @PathVariable UUID projectId,
            @PathVariable InsightType type,
            @PathVariable InsightSeverity severity) {

        return ResponseEntity.ok(
                insightService.getByProjectAndTypeAndSeverity(
                        projectId,
                        type,
                        severity
                )
        );
    }
}
