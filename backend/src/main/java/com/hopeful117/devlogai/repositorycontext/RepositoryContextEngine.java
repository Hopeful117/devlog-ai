package com.hopeful117.devlogai.repositorycontext;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.intent.model.UserGuidance;
import com.hopeful117.devlogai.repositorycontext.collector.RepositoryContextCollector;
import com.hopeful117.devlogai.repositorycontext.profile.ContextProfileResolver;
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
    private final ContextProfileResolver profileResolver;
    private final EvidenceRanker ranker;
    private final EvidenceSelector selector;
    private final ObjectMapper objectMapper;
    private final RepositoryContext.ContextBudget budget;

    public RepositoryContextEngine(
            List<RepositoryContextCollector> collectors,
            ContextProfileResolver profileResolver,
            EvidenceRanker ranker,
            EvidenceSelector selector,
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
        this.profileResolver = profileResolver;
        this.ranker = ranker;
        this.selector = selector;
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
        ContextProfile profile = profileResolver.resolve(context.analysis().type(), intent);
        ContextRequest request = new ContextRequest(
                context, intent, guidance, validatedInsights, profile, budget);
        List<RepositoryEvidence> candidates = new ArrayList<>();
        collectors.forEach(collector -> candidates.addAll(collector.collect(request)));
        List<RepositoryEvidence> ranked = ranker.rank(candidates, request);
        EvidenceSelector.SelectionResult selection = selector.select(ranked, request);
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
        int discarded = candidates.size() - selected.size();
        boolean truncated = discarded > 0;
        List<String> warnings = new ArrayList<>();
        if (truncated) warnings.add("REPOSITORY_CONTEXT_BUDGET_APPLIED");
        if (candidates.stream().anyMatch(value -> value.summary().endsWith("...")))
            warnings.add("EVIDENCE_SUMMARY_TRUNCATED");
        String digest = digest(profile, selected, byLayer, selection, warnings);
        return new RepositoryContext(VERSION, profile, selected, byLayer, budget,
                selection.usedTokens(), candidates.size(), discarded, truncated,
                selection.decisions(), warnings, digest);
    }

    private String digest(
            ContextProfile profile,
            List<RepositoryEvidence> selected,
            Map<RepositoryContextLayer, Integer> byLayer,
            EvidenceSelector.SelectionResult selection,
            List<String> warnings
    ) {
        byte[] input = objectMapper.writeValueAsBytes(Map.of(
                "version", VERSION, "profile", profile, "evidence", selected,
                "layers", byLayer, "budget", budget,
                "usedTokens", selection.usedTokens(),
                "selectionDecisions", selection.decisions(), "warnings", warnings));
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(input));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
