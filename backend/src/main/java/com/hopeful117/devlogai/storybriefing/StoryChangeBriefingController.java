package com.hopeful117.devlogai.storybriefing;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/projects")
public class StoryChangeBriefingController {
    private final StoryChangeBriefingService service;

    @GetMapping("/{projectSlug}/stories/{storyId}/change-briefing")
    public ResponseEntity<StoryChangeBriefing> get(
            @PathVariable String projectSlug, @PathVariable UUID storyId,
            @RequestParam(required = false) String description) {
        return ResponseEntity.ok(service.build(projectSlug, storyId, description));
    }
}
