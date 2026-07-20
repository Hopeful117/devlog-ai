package com.hopeful117.devlogai.decision.controller;

import com.hopeful117.devlogai.decision.dto.request.CreateDecisionRequest;
import com.hopeful117.devlogai.decision.dto.response.DecisionResponse;
import com.hopeful117.devlogai.decision.service.DecisionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/decisions")
@RequiredArgsConstructor
public class DecisionController {
    private final DecisionService decisionService;
    @PostMapping
    public ResponseEntity<DecisionResponse> create(
            @Valid @RequestBody CreateDecisionRequest request) {

        DecisionResponse response =
                decisionService.create(request);


        URI location = URI.create(
                "/api/v1/decisions/" + response.id()
        );


        return ResponseEntity
                .created(location)
                .body(response);
    }


    @GetMapping("/{id}")
    public ResponseEntity<DecisionResponse> getById(
            @PathVariable UUID id) {

        return ResponseEntity.ok(
                decisionService.getById(id)
        );
    }


    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<DecisionResponse>> getByProject(
            @PathVariable UUID projectId) {

        return ResponseEntity.ok(
                decisionService.getByProject(projectId)
        );
    }
}
