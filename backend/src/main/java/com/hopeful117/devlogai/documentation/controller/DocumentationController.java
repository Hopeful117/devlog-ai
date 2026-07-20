package com.hopeful117.devlogai.documentation.controller;

import com.hopeful117.devlogai.documentation.dto.request.CreateDocumentationRequest;
import com.hopeful117.devlogai.documentation.dto.response.DocumentationResponse;
import com.hopeful117.devlogai.documentation.entity.DocumentationType;
import com.hopeful117.devlogai.documentation.service.DocumentationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documentations")
@RequiredArgsConstructor
public class DocumentationController {
    private final DocumentationService documentationService;

    @PostMapping
    public ResponseEntity<DocumentationResponse> create(
            @Valid @RequestBody CreateDocumentationRequest request) {

        DocumentationResponse response =
                documentationService.create(request);


        URI location = URI.create(
                "/api/v1/documentations/" + response.id()
        );


        return ResponseEntity
                .created(location)
                .body(response);
    }


    @GetMapping("/{id}")
    public ResponseEntity<DocumentationResponse> getById(
            @PathVariable UUID id) {

        return ResponseEntity.ok(
                documentationService.getById(id)
        );
    }


    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<DocumentationResponse>> getByProject(
            @PathVariable UUID projectId) {

        return ResponseEntity.ok(
                documentationService.getByProject(projectId)
        );
    }


    @GetMapping("/project/{projectId}/type/{type}")
    public ResponseEntity<List<DocumentationResponse>> getByProjectAndType(
            @PathVariable UUID projectId,
            @PathVariable DocumentationType type) {

        return ResponseEntity.ok(
                documentationService.getByProjectAndType(
                        projectId,
                        type
                )
        );
    }
}
