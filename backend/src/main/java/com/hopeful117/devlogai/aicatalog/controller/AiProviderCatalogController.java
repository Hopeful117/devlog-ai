package com.hopeful117.devlogai.aicatalog.controller;

import com.hopeful117.devlogai.aicatalog.model.AiProviderCatalog;
import com.hopeful117.devlogai.aicatalog.service.AiProviderCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/catalog")
@RequiredArgsConstructor
public class AiProviderCatalogController {
    private final AiProviderCatalogService catalog;

    @GetMapping
    public ResponseEntity<AiProviderCatalog> get() {
        return ResponseEntity.ok(new AiProviderCatalog(
                catalog.catalogVersion(), catalog.all()));
    }
}
