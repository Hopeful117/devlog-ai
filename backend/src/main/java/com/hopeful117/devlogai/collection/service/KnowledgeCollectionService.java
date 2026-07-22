package com.hopeful117.devlogai.collection.service;

import java.util.UUID;

public interface KnowledgeCollectionService {

    KnowledgeCollectionResult collect(UUID analysisId);
}
