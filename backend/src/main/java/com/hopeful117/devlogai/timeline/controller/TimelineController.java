package com.hopeful117.devlogai.timeline.controller;

import com.hopeful117.devlogai.timeline.dto.TimelineResponse;
import com.hopeful117.devlogai.timeline.service.TimelineProjectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class TimelineController {

    private final TimelineProjectionService timelineService;

    @GetMapping("/{projectId}/timeline")
    public TimelineResponse getTimeline(
            @PathVariable UUID projectId
    ) {
        return timelineService.getTimeline(projectId);
    }
}