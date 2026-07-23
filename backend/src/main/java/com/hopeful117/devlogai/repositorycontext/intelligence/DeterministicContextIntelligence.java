package com.hopeful117.devlogai.repositorycontext.intelligence;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.repositorycontext.ContextProfile;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextLayer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Component
public class DeterministicContextIntelligence implements ContextIntelligence {
    static final String PLAN_VERSION = "context-intelligence-v1";
    private static final Map<String, ContextProfileDefinition> PROFILES = profiles();

    @Override
    public ContextPlan plan(AnalysisContext context, IntentDefinition intent) {
        List<String> requested = intent.contextProfiles().isEmpty()
                ? fallbackProfiles(context.analysis().type()) : intent.contextProfiles();
        List<ContextProfileDefinition> selected = requested.stream().map(key -> {
            ContextProfileDefinition definition = PROFILES.get(key);
            if (definition == null)
                throw new IllegalArgumentException("Unknown Context Profile: " + key);
            return definition;
        }).toList();
        Map<EvidenceCriterion, Integer> weights = composeWeights(selected);
        var layers = new LinkedHashSet<RepositoryContextLayer>();
        selected.forEach(profile -> layers.addAll(profile.preferredLayers()));
        int diversity = selected.stream()
                .mapToInt(ContextProfileDefinition::minimumDiverseLayers).max().orElse(1);
        return new ContextPlan(PLAN_VERSION, selected, weights, List.copyOf(layers),
                diversity, List.of(
                "INTENT_CONTEXT_PROFILES:" + String.join(",", requested),
                "PROFILE_COMPOSITION:AVERAGED_CRITERION_WEIGHTS",
                "RANKING_POLICY:multi-criteria-v1",
                "DIVERSITY_POLICY:minimum-" + diversity + "-layers"));
    }

    private Map<EvidenceCriterion, Integer> composeWeights(
            List<ContextProfileDefinition> profiles
    ) {
        Map<EvidenceCriterion, Integer> result = new EnumMap<>(EvidenceCriterion.class);
        for (EvidenceCriterion criterion : EvidenceCriterion.values()) {
            int average = (int) Math.round(profiles.stream()
                    .mapToInt(profile -> profile.criterionWeights()
                            .getOrDefault(criterion, 0))
                    .average().orElse(0));
            result.put(criterion, average);
        }
        return result;
    }

    private List<String> fallbackProfiles(AnalysisType type) {
        return switch (type) {
            case ARCHITECTURE_REVIEW -> List.of("architecture-v1", "history-v1");
            case PROJECT_EVOLUTION -> List.of("project-state-v1", "history-v1");
            default -> List.of("knowledge-extraction-v1");
        };
    }

    private static Map<String, ContextProfileDefinition> profiles() {
        Map<String, ContextProfileDefinition> result = new LinkedHashMap<>();
        register(result, profile("project-state-v1", ContextProfile.PROJECT_STATE,
                weights(30, 5, 25, 20, 15, 5),
                List.of(RepositoryContextLayer.CURRENT_ANALYSIS,
                        RepositoryContextLayer.VALIDATED_INSIGHT,
                        RepositoryContextLayer.GIT_HISTORY,
                        RepositoryContextLayer.ROADMAP), 3, 100));
        register(result, profile("architecture-v1", ContextProfile.ARCHITECTURE_REVIEW,
                weights(20, 40, 10, 5, 20, 5),
                List.of(RepositoryContextLayer.ADR,
                        RepositoryContextLayer.RELATED_SOURCE_CODE,
                        RepositoryContextLayer.VALIDATED_INSIGHT,
                        RepositoryContextLayer.COMMIT_DIFF), 3, 100));
        register(result, profile("history-v1", ContextProfile.HISTORY_ANALYSIS,
                weights(15, 5, 35, 25, 15, 5),
                List.of(RepositoryContextLayer.GIT_HISTORY,
                        RepositoryContextLayer.COMMIT_DIFF,
                        RepositoryContextLayer.ROADMAP,
                        RepositoryContextLayer.PREVIOUS_ANALYSIS), 3, 90));
        register(result, profile("documentation-v1", ContextProfile.DOCUMENTATION,
                weights(35, 10, 5, 10, 30, 10),
                List.of(RepositoryContextLayer.PROJECT_DOCUMENTATION,
                        RepositoryContextLayer.RELATED_SOURCE_CODE,
                        RepositoryContextLayer.VALIDATED_INSIGHT,
                        RepositoryContextLayer.ADR), 3, 100));
        register(result, profile("release-v1", ContextProfile.RELEASE_SUMMARY,
                weights(20, 5, 30, 30, 10, 5),
                List.of(RepositoryContextLayer.GIT_HISTORY,
                        RepositoryContextLayer.ROADMAP,
                        RepositoryContextLayer.VALIDATED_INSIGHT), 3, 100));
        register(result, profile("knowledge-extraction-v1",
                ContextProfile.KNOWLEDGE_EXTRACTION,
                weights(30, 20, 10, 10, 25, 5),
                List.of(RepositoryContextLayer.RELATED_SOURCE_CODE,
                        RepositoryContextLayer.CURRENT_ANALYSIS,
                        RepositoryContextLayer.PROJECT_DOCUMENTATION), 2, 100));
        return Map.copyOf(result);
    }

    private static ContextProfileDefinition profile(
            String key,
            ContextProfile profile,
            Map<EvidenceCriterion, Integer> weights,
            List<RepositoryContextLayer> layers,
            int diversity,
            int tokenPriority
    ) {
        return new ContextProfileDefinition(key, profile, "v1", weights, layers,
                diversity, tokenPriority);
    }

    private static Map<EvidenceCriterion, Integer> weights(
            int semantic, int architecture, int history, int recency,
            int confidence, int guidance
    ) {
        return Map.of(
                EvidenceCriterion.SEMANTIC_RELEVANCE, semantic,
                EvidenceCriterion.ARCHITECTURAL_RELEVANCE, architecture,
                EvidenceCriterion.HISTORICAL_RELEVANCE, history,
                EvidenceCriterion.RECENCY, recency,
                EvidenceCriterion.CONFIDENCE, confidence,
                EvidenceCriterion.USER_GUIDANCE_BOOST, guidance);
    }

    private static void register(
            Map<String, ContextProfileDefinition> target,
            ContextProfileDefinition profile
    ) {
        if (target.put(profile.key(), profile) != null)
            throw new IllegalStateException("Duplicate Context Profile " + profile.key());
    }
}
