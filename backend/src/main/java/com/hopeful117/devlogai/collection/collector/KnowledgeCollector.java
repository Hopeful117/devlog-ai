package com.hopeful117.devlogai.collection.collector;

public interface KnowledgeCollector {

    CollectorType type();

    String version();

    default boolean supports(CollectionContext context) {
        return context.sourceType() == com.hopeful117.devlogai.source.entity.SourceType.GIT_REPOSITORY;
    }

    CollectionResult collect(CollectionContext context);
}
