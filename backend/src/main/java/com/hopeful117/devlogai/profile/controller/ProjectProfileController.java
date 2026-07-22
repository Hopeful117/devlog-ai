package com.hopeful117.devlogai.profile.controller;

import com.hopeful117.devlogai.profile.dto.ProjectProfileResponse;
import com.hopeful117.devlogai.profile.service.ProjectProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ProjectProfileController {
    private final ProjectProfileService profileService;

    @GetMapping("/api/v1/analyses/{analysisId}/profile")
    public ResponseEntity<ProjectProfileResponse> byAnalysis(@PathVariable UUID analysisId) {
        return ResponseEntity.ok(profileService.getByAnalysis(analysisId));
    }

    @GetMapping("/api/v1/projects/{projectId}/latest-profile")
    public ResponseEntity<ProjectProfileResponse> latestByProject(@PathVariable UUID projectId) {
        return ResponseEntity.ok(profileService.getLatestByProject(projectId));
    }
}
