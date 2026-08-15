package com.hopeful117.devlogai.contextmaintenance.service;

import com.hopeful117.devlogai.contextmaintenance.dto.response.MaintenanceFindingResponse;

import java.util.UUID;

public interface KnowledgeDeduplicationService {

    MaintenanceFindingResponse mergeExactDuplicate(
            UUID projectId,
            UUID findingId,
            UUID actedBy,
            String comment
    );

    MaintenanceFindingResponse resolveSemanticDuplicate(
            UUID projectId,
            UUID findingId,
            UUID actedBy,
            String comment
    );

    MaintenanceFindingResponse resolveOverlapReview(
            UUID projectId,
            UUID findingId,
            UUID actedBy,
            String comment
    );
}
