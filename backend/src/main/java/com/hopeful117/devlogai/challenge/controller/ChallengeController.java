package com.hopeful117.devlogai.challenge.controller;

import com.hopeful117.devlogai.challenge.dto.request.CreateChallengeRequest;
import com.hopeful117.devlogai.challenge.dto.request.UpdateChallengeRequest;
import com.hopeful117.devlogai.challenge.dto.response.ChallengeResponse;
import com.hopeful117.devlogai.challenge.service.ChallengeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/challenges")
@RequiredArgsConstructor
public class ChallengeController {

    private final ChallengeService challengeService;

    @PostMapping
    public ResponseEntity<ChallengeResponse> create(
            @Valid @RequestBody CreateChallengeRequest request) {

        ChallengeResponse response = challengeService.create(request);

        URI location = URI.create(
                "/api/v1/challenges/" + response.id()
        );

        return ResponseEntity
                .created(location)
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ChallengeResponse> getById(
            @PathVariable UUID id) {

        return ResponseEntity.ok(
                challengeService.getById(id)
        );
    }

    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<ChallengeResponse>> getByProject(
            @PathVariable UUID projectId) {

        return ResponseEntity.ok(
                challengeService.getByProject(projectId)
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<ChallengeResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateChallengeRequest request) {

        return ResponseEntity.ok(
                challengeService.update(id, request)
        );
    }
}
