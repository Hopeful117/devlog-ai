package com.hopeful117.devlogai.history.service;

import java.util.List;
import java.util.UUID;

/**
 * Deterministic, family-specific history matching without public response
 * pagination or resource projection.
 */
public interface ProjectHistoryQueryService {
    List<ProjectHistoryQueryMatch> findMatches(UUID projectId, UUID sourceId, String query);
}
