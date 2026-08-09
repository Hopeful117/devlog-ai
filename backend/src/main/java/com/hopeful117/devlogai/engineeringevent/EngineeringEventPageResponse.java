package com.hopeful117.devlogai.engineeringevent;

import java.util.List;

public record EngineeringEventPageResponse(
        String version, List<EngineeringEventResponse> items, int page, int size,
        long totalElements, int totalPages, boolean hasPrevious, boolean hasNext) {
    public static final String PROJECTION_VERSION = "engineering-event-page-v1";
}
