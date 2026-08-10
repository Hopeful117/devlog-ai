package com.hopeful117.devlogai.knowledge.relation.controller;

import com.hopeful117.devlogai.knowledge.relation.dto.request.CreateKnowledgeRelationRequest;
import com.hopeful117.devlogai.knowledge.relation.dto.response.KnowledgeRelationResponse;
import com.hopeful117.devlogai.knowledge.relation.entity.EntityType;
import com.hopeful117.devlogai.knowledge.relation.service.KnowledgeRelationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/knowledge-relations")
@RequiredArgsConstructor
public class KnowledgeRelationController {

    private final KnowledgeRelationService knowledgeRelationService;

    @PostMapping
    public ResponseEntity<KnowledgeRelationResponse> create(
            @Valid @RequestBody CreateKnowledgeRelationRequest request) {

        KnowledgeRelationResponse response =
                knowledgeRelationService.create(request);

        URI location = URI.create(
                "/api/v1/knowledge-relations/" + response.id()
        );

        return ResponseEntity
                .created(location)
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<KnowledgeRelationResponse> getById(
            @PathVariable UUID id) {

        return ResponseEntity.ok(
                knowledgeRelationService.getById(id)
        );
    }

    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<KnowledgeRelationResponse>> getByProject(
            @PathVariable UUID projectId) {

        return ResponseEntity.ok(
                knowledgeRelationService.getByProject(projectId)
        );
    }

    @GetMapping("/source/{entityType}/{entityId}")
    public ResponseEntity<List<KnowledgeRelationResponse>> getBySource(
            @PathVariable EntityType entityType,
            @PathVariable UUID entityId) {

        return ResponseEntity.ok(
                knowledgeRelationService.getBySource(entityType, entityId)
        );
    }

    @GetMapping("/target/{entityType}/{entityId}")
    public ResponseEntity<List<KnowledgeRelationResponse>> getByTarget(
            @PathVariable EntityType entityType,
            @PathVariable UUID entityId) {

        return ResponseEntity.ok(
                knowledgeRelationService.getByTarget(entityType, entityId)
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        knowledgeRelationService.delete(id);

        return ResponseEntity.noContent().build();
    }
}
