package com.hopeful117.devlogai.milestone.controller;

import com.hopeful117.devlogai.milestone.dto.request.CreateMilestoneRequest;
import com.hopeful117.devlogai.milestone.dto.response.MilestoneResponse;
import com.hopeful117.devlogai.milestone.entity.MilestoneStatus;
import com.hopeful117.devlogai.milestone.service.MilestoneService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/milestones")
@RequiredArgsConstructor
public class MilestoneController {
    private final MilestoneService milestoneService;


    @PostMapping
    public ResponseEntity<MilestoneResponse> create(
            @Valid @RequestBody CreateMilestoneRequest request) {

        MilestoneResponse response =
                milestoneService.create(request);

        URI location = URI.create(
                "/api/v1/milestones/" + response.id()
        );

        return ResponseEntity
                .created(location)
                .body(response);
    }


    @GetMapping("/{id}")
    public ResponseEntity<MilestoneResponse> getById(
            @PathVariable UUID id) {

        return ResponseEntity.ok(
                milestoneService.getById(id)
        );
    }


    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<MilestoneResponse>> getByProject(
            @PathVariable UUID projectId) {

        return ResponseEntity.ok(
                milestoneService.getByProject(projectId)
        );
    }


    @GetMapping("/project/{projectId}/status/{status}")
    public ResponseEntity<List<MilestoneResponse>> getByProjectAndStatus(
            @PathVariable UUID projectId,
            @PathVariable MilestoneStatus status) {

        return ResponseEntity.ok(
                milestoneService.getByProjectAndStatus(
                        projectId,
                        status
                )
        );
    }
}
