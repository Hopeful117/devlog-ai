package com.hopeful117.devlogai.repositorycontext.ranking;

import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class RetrievalFusionService {

    private final double lexicalWeight;
    private final double semanticWeight;

    public RetrievalFusionService(
            @Value("${devlog.retrieval.fusion.lexical-weight:1.0}") double lexicalWeight,
            @Value("${devlog.retrieval.fusion.semantic-weight:1.0}") double semanticWeight) {
        this.lexicalWeight = lexicalWeight;
        this.semanticWeight = semanticWeight;
    }

    /**
     * Reciprocal Rank Fusion across lexical and semantic retrieval channels.
     * Returns composite 0–100 scores keyed by evidence reference.
     * When semanticRanked is empty, returns an empty map so the caller falls
     * back to term-model scores (existing behavior preserved).
     */
    public Map<String, Integer> fuse(
            List<RepositoryEvidence> lexicalRanked,
            List<RepositoryEvidence> semanticRanked,
            int k) {
        if (semanticRanked.isEmpty()) return Map.of();

        Map<String, Integer> lexicalRanks = indexByRank(lexicalRanked);
        Map<String, Integer> semanticRanks = indexByRank(semanticRanked);

        Set<String> refs = new LinkedHashSet<>();
        lexicalRanked.forEach(e -> refs.add(e.reference()));
        semanticRanked.forEach(e -> refs.add(e.reference()));

        double maxPossible = (lexicalWeight + semanticWeight) / (k + 1);

        Map<String, Integer> result = new LinkedHashMap<>();
        for (String ref : refs) {
            double raw = 0.0;
            if (lexicalRanks.containsKey(ref))
                raw += lexicalWeight / (k + lexicalRanks.get(ref));
            if (semanticRanks.containsKey(ref))
                raw += semanticWeight / (k + semanticRanks.get(ref));
            result.put(ref, (int) Math.round(Math.min(100.0, raw / maxPossible * 100.0)));
        }
        return Collections.unmodifiableMap(result);
    }

    private Map<String, Integer> indexByRank(List<RepositoryEvidence> ranked) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < ranked.size(); i++)
            map.put(ranked.get(i).reference(), i + 1);
        return map;
    }
}
