package com.hopeful117.devlogai.intent.service;

import com.hopeful117.devlogai.intent.model.InsightType;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.hopeful117.devlogai.proposal.entity.ProposalType;
import com.hopeful117.devlogai.intent.model.IntentExecutionMode;

@Component
public class IntentCatalog {
    private static final String PROJECT_STATE_PROFILE = "project-state-v1";
    private static final String HISTORY_PROFILE = "history-v1";
    private static final Map<String, IntentDefinition> INTENTS = catalog();

    public IntentDefinition resolve(String key) {
        IntentDefinition intent = INTENTS.get(key);
        if (intent == null) throw new EntityNotFoundException("Intent", key);
        return intent;
    }

    public IntentDefinition resolve(String id, String version) {
        if (id == null || version == null) throw new EntityNotFoundException("Intent", "missing");
        return resolve(id + "-" + version);
    }

    public List<IntentDefinition> all() { return List.copyOf(INTENTS.values()); }

    private static Map<String, IntentDefinition> catalog() {
        Map<String, IntentDefinition> result = new LinkedHashMap<>();
        register(result, intent("describe-project", "Décrire objectivement le projet analysé.",
                List.of(InsightType.PROJECT_PRESENTATION, InsightType.ARCHITECTURE_DESCRIPTION,
                        InsightType.TECHNOLOGY_DESCRIPTION), "describe-project-prompt-v1"));
        register(result, intent("generate-readme", "Proposer les informations structurées nécessaires à un README.",
                List.of(InsightType.INSTALLATION, InsightType.USAGE, InsightType.REQUIREMENTS,
                        InsightType.PROJECT_PRESENTATION), "generate-readme-prompt-v1"));
        register(result, intent("architecture-overview", "Présenter les caractéristiques architecturales démontrables du projet.",
                List.of(InsightType.ARCHITECTURE_DESCRIPTION, InsightType.TECHNOLOGY_DESCRIPTION,
                        InsightType.INFRASTRUCTURE_DESCRIPTION, InsightType.API_DESCRIPTION),
                "architecture-overview-prompt-v1"));
        register(result, new IntentDefinition(
                "analyze-engineering-event", "v1",
                "Proposer des Engineering Events fondés sur un commit et son premier parent.",
                ProposalType.ENGINEERING_EVENT, IntentExecutionMode.DEDICATED_ENGINEERING_EVENT,
                List.of(),
                List.of("Utiliser uniquement le contexte d'évolution sélectionné.",
                        "Ne jamais inférer intention ou causalité sans preuve.",
                        "Retourner zéro proposition lorsque les preuves sont insuffisantes."),
                eventOutputContract(), "analyze-engineering-event-prompt-v1",
                List.of(HISTORY_PROFILE, PROJECT_STATE_PROFILE)));
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(result));
    }

    private static IntentDefinition intent(String id, String objective,
                                           List<InsightType> types, String template) {
        return new IntentDefinition(id, "v1", objective,
                ProposalType.INSIGHT, IntentExecutionMode.GENERIC, types,
                List.of("Utiliser uniquement AnalysisContext.",
                        "Ne produire que des propositions traçables et soumises à validation humaine.",
                        "Ne jamais présenter une proposition comme une connaissance validée."),
                outputContract(types), template, contextProfiles(id));
    }

    private static List<String> contextProfiles(String intentId) {
        return switch (intentId) {
            case "describe-project" -> List.of(PROJECT_STATE_PROFILE, HISTORY_PROFILE);
            case "architecture-overview" -> List.of("architecture-v1", HISTORY_PROFILE);
            case "generate-readme" -> List.of("documentation-v1", PROJECT_STATE_PROFILE);
            default -> throw new IllegalArgumentException(
                    "No Context Profiles registered for Intent " + intentId);
        };
    }

    private static Map<String, Object> outputContract(List<InsightType> types) {
        return Map.of(
                "type", "object",
                "root", "proposals",
                "structured", true,
                "minimumProposalCount", 0,
                "maximumProposalCount", 10,
                "allowedInsightTypes", types.stream().map(Enum::name).toList(),
                "requiredProposalFields", List.of(
                        "insightType", "title", "summary", "rationale", "confidence",
                        "supportingFactIds", "supportingObservationIds", "evidenceReferences"));
    }

    private static Map<String, Object> eventOutputContract() {
        return Map.of(
                "type", "object", "root", "proposals", "structured", true,
                "minimumProposalCount", 0, "maximumProposalCount", 10,
                "proposalType", ProposalType.ENGINEERING_EVENT.name(),
                "schemaVersion", "engineering-event-proposal-v1",
                "allowedCategories", List.of("FEATURE_INTRODUCTION", "BUG_RESOLUTION",
                        "ARCHITECTURE_CHANGE", "TECHNOLOGY_CHANGE",
                        "ENGINEERING_IMPROVEMENT", "INFRASTRUCTURE_CHANGE"),
                "requiredProposalFields", List.of("schemaVersion", "category", "title",
                        "summary", "significance", "confidence", "supportingFactIds",
                        "supportingObservationIds", "evidenceReferences"));
    }

    private static void register(Map<String, IntentDefinition> target, IntentDefinition intent) {
        if (target.put(intent.key(), intent) != null)
            throw new IllegalStateException("Duplicate Intent: " + intent.key());
    }
}
