package com.hopeful117.devlogai.collection.collector;

import com.hopeful117.devlogai.collection.workspace.SynchronizedWorkspace;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.entity.SourceType;

import java.util.List;

public interface KnowledgeCollector {

    String name();

    boolean supports(SourceType sourceType);

    List<CollectedFact> collect(
            Source source,
            SynchronizedWorkspace workspace
    );
}
