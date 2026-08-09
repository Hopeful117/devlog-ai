package com.hopeful117.devlogai.repositorycontext;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.intent.model.UserGuidance;
import com.hopeful117.devlogai.repositorycontext.collector.RepositoryContextCollector;
import com.hopeful117.devlogai.repositorycontext.enrichment.SelectedFileContentEnricher;
import com.hopeful117.devlogai.repositorycontext.enrichment.SelectedJavaSymbolEnricher;
import com.hopeful117.devlogai.repositorycontext.intelligence.ContextIntelligence;
import com.hopeful117.devlogai.repositorycontext.intelligence.ContextPlan;
import com.hopeful117.devlogai.repositorycontext.ranking.EvidenceRanker;
import com.hopeful117.devlogai.repositorycontext.selection.EvidenceSelector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
public class RepositoryContextEngine implements RepositoryContextService {
    static final String VERSION = "repository-context-engine-v1";
    private final List<RepositoryContextCollector> collectors;
    private final ContextIntelligence contextIntelligence;
    private final EvidenceRanker ranker;
    private final EvidenceSelector selector;
    private final SelectedJavaSymbolEnricher symbolEnricher;
    private final SelectedFileContentEnricher contentEnricher;
    private final ObjectMapper objectMapper;
    private final RepositoryContext.ContextBudget budget;

    public RepositoryContextEngine(
            List<RepositoryContextCollector> collectors,
            ContextIntelligence contextIntelligence,
            EvidenceRanker ranker,
            EvidenceSelector selector,
            SelectedJavaSymbolEnricher symbolEnricher,
            SelectedFileContentEnricher contentEnricher,
            ObjectMapper objectMapper,
            @Value("${devlog.repository-context.max-evidence-items:60}") int maximumEvidenceItems,
            @Value("${devlog.repository-context.max-summary-characters:500}") int maximumSummaryCharacters,
            @Value("${devlog.repository-context.max-history-items:20}") int maximumHistoryItems,
            @Value("${devlog.repository-context.max-tokens:6000}") int maximumTokens
    ) {
        if (maximumEvidenceItems < 1 || maximumSummaryCharacters < 50
                || maximumHistoryItems < 0 || maximumTokens < 1)
            throw new IllegalArgumentException("Repository context limits are invalid");
        this.collectors = List.copyOf(collectors);
        this.contextIntelligence = contextIntelligence;
        this.ranker = ranker;
        this.selector = selector;
        this.symbolEnricher = symbolEnricher;
        this.contentEnricher = contentEnricher;
        this.objectMapper = objectMapper;
        this.budget = new RepositoryContext.ContextBudget(maximumEvidenceItems,
                maximumSummaryCharacters, maximumHistoryItems, maximumTokens);
    }

    @Override
    public RepositoryContext build(
            AnalysisContext context,
            IntentDefinition intent,
            UserGuidance guidance,
            List<Insight> validatedInsights
    ) {
        ContextPlan contextPlan = contextIntelligence.plan(context, intent);
        ContextRequest request = new ContextRequest(
                context, intent, guidance, validatedInsights, contextPlan, budget);
        List<RepositoryEvidence> candidates = new ArrayList<>();
        collectors.forEach(collector -> candidates.addAll(collector.collect(request)));
        List<RepositoryEvidence> ranked = ranker.rank(candidates, request);
        EvidenceSelector.SelectionResult pathSelection = selector.select(ranked, request);
        SelectedJavaSymbolEnricher.EnrichmentResult symbolEnrichment =
                symbolEnricher.enrich(request, pathSelection);
        SelectedFileContentEnricher.EnrichmentResult contentEnrichment =
                contentEnricher.enrich(request, symbolEnrichment.selection());
        EvidenceSelector.SelectionResult selection = contentEnrichment.selection();
        List<RepositoryEvidence> selected = selection.selected().stream()
                .sorted(Comparator.comparingInt(
                                (RepositoryEvidence value) -> value.layer().ordinal())
                        .thenComparing(Comparator.comparingInt(
                                RepositoryEvidence::relevanceScore).reversed())
                        .thenComparing(RepositoryEvidence::reference))
                .toList();
        Map<RepositoryContextLayer, Integer> byLayer =
                new EnumMap<>(RepositoryContextLayer.class);
        selected.forEach(value -> byLayer.merge(value.layer(), 1, Integer::sum));
        RepositoryContextDiagnostics diagnostics = diagnostics(
                candidates, selected, contextPlan);
        int discarded = candidates.size() - selected.size();
        boolean truncated = discarded > 0;
        List<String> warnings = new ArrayList<>();
        if (selection.decisions().stream().anyMatch(value ->
                value.reason().equals("EVIDENCE_ITEM_BUDGET_EXCEEDED")
                        || value.reason().equals("TOKEN_BUDGET_EXCEEDED")))
            warnings.add("REPOSITORY_CONTEXT_BUDGET_APPLIED");
        if (candidates.stream().anyMatch(value -> value.summary().endsWith("...")))
            warnings.add("EVIDENCE_SUMMARY_TRUNCATED");
        warnings.addAll(symbolEnrichment.warnings());
        warnings.addAll(contentEnrichment.warnings());
        String digest = digest(contextPlan, selected, byLayer, diagnostics,
                selection, warnings);
        return new RepositoryContext(VERSION, contextPlan.primaryProfile(),
                contextPlan.profileKeys(), contextPlan.planVersion(),
                contextPlan.explanations(), selected, byLayer, diagnostics, budget,
                selection.usedTokens(), candidates.size(), discarded, truncated,
                selection.decisions(), warnings, digest);
    }

    private String digest(
            ContextPlan contextPlan,
            List<RepositoryEvidence> selected,
            Map<RepositoryContextLayer, Integer> byLayer,
            RepositoryContextDiagnostics diagnostics,
            EvidenceSelector.SelectionResult selection,
            List<String> warnings
    ) {
        byte[] input = objectMapper.writeValueAsBytes(Map.ofEntries(
                Map.entry("version", VERSION),
                Map.entry("profiles", contextPlan.profileKeys()),
                Map.entry("contextPlanVersion", contextPlan.planVersion()),
                Map.entry("evidence", selected), Map.entry("layers", byLayer),
                Map.entry("budget", budget),
                Map.entry("precisionPolicy", contextPlan.precisionPolicy()),
                Map.entry("diagnostics", diagnostics),
                Map.entry("usedTokens", selection.usedTokens()),
                Map.entry("selectionDecisions", selection.decisions()),
                Map.entry("warnings", warnings)));
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(input));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private RepositoryContextDiagnostics diagnostics(
            List<RepositoryEvidence> candidates,
            List<RepositoryEvidence> selected,
            ContextPlan contextPlan
    ) {
        Map<RepositoryContextLayer, Integer> candidatesByLayer =
                new EnumMap<>(RepositoryContextLayer.class);
        Map<String, Integer> candidatesByKind = new java.util.TreeMap<>();
        Map<String, Integer> selectedByKind = new java.util.TreeMap<>();
        candidates.forEach(value -> {
            candidatesByLayer.merge(value.layer(), 1, Integer::sum);
            candidatesByKind.merge(value.kind(), 1, Integer::sum);
        });
        selected.forEach(value -> selectedByKind.merge(value.kind(), 1, Integer::sum));
        List<RepositoryContextDiagnostics.PreferredLayerAvailability> availability =
                contextPlan.preferredLayers().stream().map(layer -> {
                    boolean available = candidatesByLayer.containsKey(layer);
                    return new RepositoryContextDiagnostics.PreferredLayerAvailability(
                            layer, available,
                            available ? null : "NO_CANDIDATE_FOR_PREFERRED_LAYER");
                }).toList();
        int unique = (int) candidates.stream().map(RepositoryEvidence::reference)
                .distinct().count();
        return new RepositoryContextDiagnostics(candidatesByLayer, candidatesByKind,
                selectedByKind, availability, unique, candidates.size() - unique);
    }
}
