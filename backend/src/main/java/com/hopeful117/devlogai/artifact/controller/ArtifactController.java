package com.hopeful117.devlogai.artifact.controller;

import com.hopeful117.devlogai.artifact.dto.request.CreateArtifactRequest;
import com.hopeful117.devlogai.artifact.dto.response.ArtifactResponse;
import com.hopeful117.devlogai.artifact.entity.ArtifactType;
import com.hopeful117.devlogai.artifact.service.ArtifactService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/artifacts")
public class ArtifactController {
    private final ArtifactService artifactService;

    @PostMapping
    public ResponseEntity<ArtifactResponse> create(
            @Valid @RequestBody CreateArtifactRequest request) {

        ArtifactResponse response =
                artifactService.create(request);


        URI location = URI.create(
                "/api/v1/artifacts/" + response.id()
        );


        return ResponseEntity
                .created(location)
                .body(response);
    }


    @GetMapping("/{id}")
    public ResponseEntity<ArtifactResponse> getById(
            @PathVariable UUID id) {

        return ResponseEntity.ok(
                artifactService.getById(id)
        );
    }


    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<ArtifactResponse>> getByProject(
            @PathVariable UUID projectId) {

        return ResponseEntity.ok(
                artifactService.getByProject(projectId)
        );
    }


    @GetMapping("/project/{projectId}/type/{type}")
    public ResponseEntity<List<ArtifactResponse>> getByProjectAndType(
            @PathVariable UUID projectId,
            @PathVariable ArtifactType type) {

        return ResponseEntity.ok(
                artifactService.getByProjectAndType(
                        projectId,
                        type
                )
        );
    }
}
