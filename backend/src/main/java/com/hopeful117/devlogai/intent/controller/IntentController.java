package com.hopeful117.devlogai.intent.controller;

import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.intent.service.IntentCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/v1/intents")
@RequiredArgsConstructor
public class IntentController {
    private final IntentCatalog catalog;

    @GetMapping
    public ResponseEntity<List<IntentDefinition>> list() {
        return ResponseEntity.ok(catalog.all());
    }
}
