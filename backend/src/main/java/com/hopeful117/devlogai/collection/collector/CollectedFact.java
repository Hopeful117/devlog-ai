package com.hopeful117.devlogai.collection.collector;

import com.hopeful117.devlogai.fact.entity.FactType;

import java.util.List;

public record CollectedFact(
        FactType type,
        String content,
        String source,
        List<String> evidenceReferences
) {
    public CollectedFact {
        evidenceReferences = List.copyOf(evidenceReferences);
    }
}
