package com.hopeful117.devlogai.source.controller;

import com.hopeful117.devlogai.source.dto.request.CreateSourceRequest;
import com.hopeful117.devlogai.source.dto.request.UpdateSourceActivationRequest;
import com.hopeful117.devlogai.source.dto.response.SourceResponse;
import com.hopeful117.devlogai.source.service.SourceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/sources")
public class SourceController {

    private final SourceService sourceService;

    @PostMapping
    public ResponseEntity<SourceResponse> create(
            @Valid @RequestBody CreateSourceRequest request
    ) {
        SourceResponse response = sourceService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/sources/" + response.id()))
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SourceResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(sourceService.getById(id));
    }

    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<SourceResponse>> getByProject(
            @PathVariable UUID projectId
    ) {
        return ResponseEntity.ok(sourceService.getByProject(projectId));
    }

    @PatchMapping("/{id}/activation")
    public ResponseEntity<SourceResponse> setActive(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSourceActivationRequest request
    ) {
        return ResponseEntity.ok(sourceService.setActive(id, request.active()));
    }
}
