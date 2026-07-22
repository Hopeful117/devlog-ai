package com.hopeful117.devlogai.intent.service;

import com.hopeful117.devlogai.intent.model.InsightType;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class IntentCatalog {
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
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(result));
    }

    private static IntentDefinition intent(String id, String objective,
                                           List<InsightType> types, String template) {
        return new IntentDefinition(id, "v1", objective, types,
                List.of("Utiliser uniquement AnalysisContext.",
                        "Ne produire que des propositions traçables et soumises à validation humaine.",
                        "Ne jamais présenter une proposition comme une connaissance validée."),
                Map.of("type", "object", "root", "proposals", "structured", true), template);
    }

    private static void register(Map<String, IntentDefinition> target, IntentDefinition intent) {
        if (target.put(intent.key(), intent) != null)
            throw new IllegalStateException("Duplicate Intent: " + intent.key());
    }
}
