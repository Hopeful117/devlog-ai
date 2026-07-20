package com.hopeful117.devlogai.knowledge.controller;

import com.hopeful117.devlogai.knowledge.dto.request.CreateKnowledgeEventRequest;
import com.hopeful117.devlogai.knowledge.dto.response.KnowledgeEventResponse;
import com.hopeful117.devlogai.knowledge.service.KnowledgeEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@RequestMapping("/api/v1/knowledge-events")
public class KnowledgeEventController {
    private final KnowledgeEventService knowledgeEventService;


    @PostMapping
    public ResponseEntity<KnowledgeEventResponse> create(
            @Valid @RequestBody CreateKnowledgeEventRequest request) {

        KnowledgeEventResponse response =
                knowledgeEventService.create(request);

        URI location = URI.create(
                "/api/v1/knowledge-events/" + response.id()
        );

        return ResponseEntity
                .created(location)
                .body(response);
    }


    @GetMapping("/{id}")
    public ResponseEntity<KnowledgeEventResponse> getById(
            @PathVariable UUID id) {

        return ResponseEntity.ok(
                knowledgeEventService.getById(id)
        );
    }


    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<KnowledgeEventResponse>> getByProject(
            @PathVariable UUID projectId) {

        return ResponseEntity.ok(
                knowledgeEventService.getByProject(projectId)
        );
    }
}
