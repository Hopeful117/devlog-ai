package com.hopeful117.devlogai.collection.collector;

import com.hopeful117.devlogai.fact.entity.FactType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

final class FactAccumulator {
    private final String version;
    private final String revision;
    private final int maxPerType;
    private final String sourceReference;
    private final List<CollectedFact> facts = new ArrayList<>();
    private final List<CollectionWarning> warnings = new ArrayList<>();
    private final Map<FactType, Integer> counts = new EnumMap<>(FactType.class);
    private final Set<String> fingerprints = new HashSet<>();

    FactAccumulator(String version, String revision, String sourceReference, int maxPerType) {
        this.version = version;
        this.revision = revision;
        this.sourceReference = sourceReference;
        this.maxPerType = maxPerType;
    }

    void add(FactType type, String content, String... evidence) {
        int count = counts.getOrDefault(type, 0);
        if (count >= maxPerType) {
            if (warnings.stream().noneMatch(w -> w.code().equals("MAX_FACTS_" + type.name()))) {
                warnings.add(new CollectionWarning("MAX_FACTS_" + type.name(),
                        "Maximum Facts reached for type " + type.name()));
            }
            return;
        }
        List<String> references = new ArrayList<>(List.of(evidence));
        references.add(sourceReference);
        CollectedFact fact = CollectedFact.create(version, type, content, references, revision);
        if (!fingerprints.add(fact.fingerprint())) return;
        facts.add(fact);
        counts.put(type, count + 1);
    }

    void warnings(List<CollectionWarning> added) { warnings.addAll(added); }
    void warning(String code, String message) {
        warnings.add(new CollectionWarning(code, message));
    }
    List<CollectedFact> facts() { return List.copyOf(facts); }
    List<CollectionWarning> warnings() { return List.copyOf(warnings); }
}
