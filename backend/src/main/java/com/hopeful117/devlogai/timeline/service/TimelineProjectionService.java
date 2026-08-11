package com.hopeful117.devlogai.timeline.service;

import com.hopeful117.devlogai.timeline.dto.TimelineResponse;

import java.util.UUID;

public interface TimelineProjectionService {

    TimelineResponse getTimeline(UUID projectId);
}