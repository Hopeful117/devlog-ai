package com.hopeful117.devlogai.knowledge.selection;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.analysis.diagnostics.entity.AnalysisExecutionDiagnostic;
import com.hopeful117.devlogai.analysis.diagnostics.repository.AnalysisExecutionDiagnosticRepository;
import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.insight.repository.InsightRepository;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.intent.model.UserGuidance;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class KnowledgeSelectionServiceImpl implements KnowledgeSelectionService {
    static final String VERSION = "knowledge-selection-v1";
    static final SelectedKnowledge.KnowledgeBudget BUDGET =
            new SelectedKnowledge.KnowledgeBudget(40, 25, 10);

    private final AnalysisExecutionDiagnosticRepository diagnosticRepository;
    private final InsightRepository insightRepository;
    private final ObjectMapper objectMapper;

    @Override
    public SelectedKnowledge select(AnalysisContext context, IntentDefinition intent,
                                    UserGuidance guidance) {
        requireMandatoryKnowledge(context, intent);
        Comparator<AnalysisContext.ObservationSnapshot> observationOrder = Comparator
                .comparingInt((AnalysisContext.ObservationSnapshot value) ->
                        observationScore(intent.id(), value) + guidanceScore(guidance, value.type() + " " + value.content())).reversed()
                .thenComparing(value -> value.type().name())
                .thenComparing(value -> value.id().toString());
        Comparator<AnalysisContext.FactSnapshot> factOrder = Comparator
                .comparingInt((AnalysisContext.FactSnapshot value) ->
                        factScore(intent.id(), value) + guidanceScore(guidance, value.type() + " " + value.content())).reversed()
                .thenComparing(value -> value.type().name())
                .thenComparing(value -> value.id().toString());

        List<AnalysisContext.ObservationSnapshot> observations = context.observations().stream()
                .sorted(observationOrder).distinct().limit(BUDGET.maximumObservations()).toList();
        List<AnalysisContext.FactSnapshot> facts = context.facts().stream()
                .sorted(factOrder)
                .filter(distinctByKey(value -> value.type() + "\u0000" + value.content()))
                .limit(BUDGET.maximumFacts()).toList();
        List<Insight> insightCandidates = insightRepository
                .findByProjectIdOrderByCreatedAtDesc(context.project().id()).stream()
                .sorted(Comparator.comparing(Insight::getCreatedAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Insight::getId))
                .toList();
        List<SelectedKnowledge.InsightSnapshot> insights = insightCandidates.stream()
                .limit(BUDGET.maximumInsights()).map(this::toInsight).toList();
        AnalysisExecutionDiagnostic diagnostic = diagnosticRepository.findById(context.analysis().id())
                .orElseThrow(() -> new IllegalStateException(
                        "Mandatory analysis diagnostics are unavailable"));
        SelectedKnowledge.DiagnosticSnapshot diagnostics = new SelectedKnowledge.DiagnosticSnapshot(
                diagnostic.isCollectionComplete(), diagnostic.isTruncated(),
                diagnostic.getWarningCount(), diagnostic.getErrorCount());
        int candidates = context.observations().size() + context.facts().size()
                + insightCandidates.size();
        int selected = 1 + observations.size() + facts.size() + insights.size() + 1;
        var metadata = new SelectedKnowledge.SelectionMetadata(
                VERSION,
                List.of("INTENT_SPECIFIC_RANKING", "USER_GUIDANCE_KEYWORD_BOOST", "STABLE_TYPE_AND_ID_ORDER",
                        "DUPLICATE_FACT_CONTENT_ELIMINATION", "KNOWLEDGE_BUDGET"),
                selected, Math.max(0, candidates + 2 - selected), BUDGET,
                diagnostic.isCollectionComplete() ? "COMPLETE" : "PARTIAL");
        String digest = digest(context, observations, facts, diagnostics, insights, metadata);
        return new SelectedKnowledge(context.project(), context.analysis(), context.projectProfile(),
                observations, facts, diagnostics, insights, metadata, digest);
    }

    private void requireMandatoryKnowledge(AnalysisContext context, IntentDefinition intent) {
        if (context == null || context.project() == null || context.analysis() == null
                || context.projectProfile() == null) {
            throw new IllegalStateException("Mandatory project knowledge is unavailable");
        }
        if (!context.analysis().intentId().equals(intent.id())
                || !context.analysis().intentVersion().equals(intent.version())) {
            throw new IllegalArgumentException("Intent does not match AnalysisContext");
        }
    }

    private int observationScore(String intentId, AnalysisContext.ObservationSnapshot value) {
        String type = value.type().name();
        if (intentId.equals("architecture-overview"))
            return containsAny(type, "ARCHITECTURE", "COMMUNICATION", "CONTAINER", "MODULE") ? 100 : 10;
        if (intentId.equals("generate-readme"))
            return containsAny(type, "DOCUMENTATION", "APPLICATION", "TEST", "CONTAINER") ? 100 : 20;
        return containsAny(type, "ARCHITECTURE", "APPLICATION", "TECHNOLOGY", "CONTAINER") ? 80 : 40;
    }

    private int factScore(String intentId, AnalysisContext.FactSnapshot value) {
        String type = value.type().name();
        if (intentId.equals("architecture-overview"))
            return containsAny(type, "SPRING", "DOCKER", "REST", "BUILD", "MODULE") ? 100 : 10;
        if (intentId.equals("generate-readme"))
            return containsAny(type, "README", "DOCUMENTATION", "BUILD", "DOCKER", "JAVA_VERSION") ? 100 : 20;
        return containsAny(type, "REPOSITORY", "BUILD", "SPRING", "DOCKER", "README") ? 80 : 40;
    }

    private boolean containsAny(String value, String... candidates) {
        return Arrays.stream(candidates).anyMatch(value::contains);
    }

    private int guidanceScore(UserGuidance guidance, String candidate) {
        if (guidance == null || guidance.isEmpty()) return 0;
        String terms = String.join(" ", guidance.priorities()) + " "
                + Objects.toString(guidance.focus(), "") + " "
                + Objects.toString(guidance.outputContext(), "");
        String normalizedCandidate = candidate.toLowerCase(Locale.ROOT);
        return (int) Arrays.stream(terms.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(term -> term.length() >= 3)
                .distinct()
                .filter(normalizedCandidate::contains)
                .count() * 5;
    }

    private <T> java.util.function.Predicate<T> distinctByKey(
            java.util.function.Function<T, String> keyExtractor) {
        Set<String> seen = new HashSet<>();
        return value -> seen.add(keyExtractor.apply(value));
    }

    private SelectedKnowledge.InsightSnapshot toInsight(Insight insight) {
        return new SelectedKnowledge.InsightSnapshot(insight.getId(), insight.getAnalysis().getId(),
                insight.getType(), insight.getSeverity(), insight.getTitle(), insight.getContent());
    }

    private String digest(AnalysisContext context,
                          List<AnalysisContext.ObservationSnapshot> observations,
                          List<AnalysisContext.FactSnapshot> facts,
                          SelectedKnowledge.DiagnosticSnapshot diagnostics,
                          List<SelectedKnowledge.InsightSnapshot> insights,
                          SelectedKnowledge.SelectionMetadata metadata) {
        record DigestInput(Object project, Object analysis, Object profile, Object selectedObservations,
                           Object selectedFacts, Object diagnostic, Object selectedInsights,
                           Object selectionMetadata) { }
        byte[] serialized = objectMapper.writeValueAsString(new DigestInput(
                context.project(), context.analysis(), context.projectProfile(), observations,
                facts, diagnostics, insights, metadata)).getBytes(StandardCharsets.UTF_8);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(serialized));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
