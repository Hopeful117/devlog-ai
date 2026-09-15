package com.hopeful117.devlogai.ai.reference;

import com.hopeful117.devlogai.knowledge.selection.SelectedKnowledge;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AiReferenceRegistryFactory {
    private AiReferenceRegistryFactory() { }

    public static AiReferenceRegistry create(SelectedKnowledge knowledge) {
        List<AiReferenceBinding> bindings = new ArrayList<>();
        Map<String, AiReferenceBinding> registered = new LinkedHashMap<>();
        Set<AiReference> architectureKnowledgeReferences = new java.util.LinkedHashSet<>();
        add(bindings, AiReferenceType.PROJECT, AiReferenceScope.PROJECT,
                "PROJECT", knowledge.project().id().toString(), "project:" + knowledge.project().id(), Set.of(), registered);
        add(bindings, AiReferenceType.ANALYSIS, AiReferenceScope.ANALYSIS_CONTEXT,
                "ANALYSIS", knowledge.analysis().id().toString(), "A001", Set.of(), registered);
        if (knowledge.projectProfile() != null) {
            add(bindings, AiReferenceType.PROJECT_PROFILE, AiReferenceScope.ANALYSIS_CONTEXT,
                    "PROJECT_PROFILE", knowledge.projectProfile().id().toString(), "PP001", Set.of(), registered);
        }
        int fact = 1;
        for (var value : knowledge.selectedFacts()) {
            add(bindings, AiReferenceType.FACT, AiReferenceScope.ANALYSIS_CONTEXT,
                    "FACT", value.id().toString(), "F%03d".formatted(fact++), Set.of("SUPPORTING_FACT"), registered);
        }
        int observation = 1;
        for (var value : knowledge.selectedObservations()) {
            add(bindings, AiReferenceType.OBSERVATION, AiReferenceScope.ANALYSIS_CONTEXT,
                    "OBSERVATION", value.id().toString(), "O%03d".formatted(observation++), Set.of("SUPPORTING_OBSERVATION"), registered);
        }
        for (var value : knowledge.selectedInsights()) {
            add(bindings, AiReferenceType.INSIGHT, AiReferenceScope.PROJECT,
                    "INSIGHT", value.id().toString(), "insight:" + value.id(), Set.of(), registered);
        }
        // Architecture knowledge is an enriched projection of an Insight in the current model.
        // Its insightId therefore resolves through the existing INSIGHT binding.
        for (var value : knowledge.existingArchitectureKnowledge()) {
            if (value.insightId() != null) {
                add(bindings, AiReferenceType.INSIGHT, AiReferenceScope.PROJECT,
                        "INSIGHT", value.insightId().toString(), "insight:" + value.insightId(), Set.of(), registered);
                architectureKnowledgeReferences.add(new AiReference(AiReferenceType.INSIGHT,
                        "insight:" + value.insightId(), AiReferenceScope.PROJECT));
            }
        }
        for (var value : knowledge.selectedEngineeringEvents()) {
            add(bindings, AiReferenceType.ENGINEERING_EVENT, AiReferenceScope.PROJECT,
                    "ENGINEERING_EVENT", value.id().toString(), "event:" + value.id(), Set.of(), registered);
        }
        for (var value : knowledge.selectedHumanContextInputs()) {
            add(bindings, AiReferenceType.HUMAN_CONTEXT, AiReferenceScope.PROJECT,
                    "HUMAN_CONTEXT", value.id().toString(), "human-context:" + value.id(), Set.of(), registered);
        }
        if (knowledge.repositoryContext() != null) {
            for (RepositoryEvidence value : knowledge.repositoryContext().evidence()) {
                add(bindings, AiReferenceType.REPOSITORY_EVIDENCE, AiReferenceScope.REPOSITORY,
                        "REPOSITORY_EVIDENCE", value.reference(), value.reference(), Set.of("EVIDENCE_REFERENCE"), registered);
            }
        }
        return new AiReferenceRegistry(bindings, architectureKnowledgeReferences);
    }

    private static void add(List<AiReferenceBinding> bindings, AiReferenceType type,
            AiReferenceScope scope, String entityType, String identity,
            String reference, Set<String> capabilities,
            Map<String, AiReferenceBinding> registered) {
        String key = type + "\u0000" + scope + "\u0000" + identity;
        AiReferenceBinding existing = registered.get(key);
        if (existing != null) {
            if (!existing.reference().equals(new AiReference(type, reference, scope))
                    || !existing.groundingCapabilities().equals(capabilities)) {
                throw new IllegalArgumentException("conflicting AI reference binding: " + key);
            }
            return;
        }
        AiReferenceBinding binding = new AiReferenceBinding(
                new AiReference(type, reference, scope), entityType, identity, capabilities);
        registered.put(key, binding);
        bindings.add(binding);
    }

}
