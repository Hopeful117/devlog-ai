package com.hopeful117.devlogai.engineeringcontext.controller;

import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContext;
import com.hopeful117.devlogai.engineeringcontext.EngineeringContextFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/projects")
public class EngineeringContextController {
    private final EngineeringContextFacade engineeringContextFacade;

    @GetMapping("/{projectSlug}/engineering-context")
    public ResponseEntity<EngineeringContext> getEngineeringContext(
            @PathVariable String projectSlug,
            @RequestParam String intent,
            @RequestParam(required = false) List<String> files,
            @RequestParam(required = false) UUID storyId
    ) {
        // Convert null files to empty list for consistent behavior
        List<String> effectiveFiles = files != null ? files : List.of();
        return ResponseEntity.ok(
                engineeringContextFacade.getEngineeringContext(
                        projectSlug,
                        intent,
                        effectiveFiles,
                        storyId
                )
        );
    }
}