package com.hopeful117.devlogai.knowledge.selection;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.analysis.diagnostics.entity.AnalysisExecutionDiagnostic;
import com.hopeful117.devlogai.analysis.diagnostics.repository.AnalysisExecutionDiagnosticRepository;
import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.insight.entity.InsightStatus;
import com.hopeful117.devlogai.insight.repository.InsightRepository;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.intent.model.UserGuidance;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextLayer;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextService;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
public class KnowledgeSelectionServiceImpl implements KnowledgeSelectionService {
    static final String VERSION = "knowledge-selection-v4";
    private static final String BUILD = "BUILD";
    private static final String CONTAINER = "CONTAINER";
    private static final String DOCKER = "DOCKER";
    private static final Set<String> ARCHITECTURE_SOURCE_TYPES = Set.of(
            "ARCHITECTURE_DESCRIPTION", "TECHNOLOGY_DESCRIPTION",
            "INFRASTRUCTURE_DESCRIPTION", "API_DESCRIPTION");
    static final SelectedKnowledge.KnowledgeBudget BUDGET =
            new SelectedKnowledge.KnowledgeBudget(40, 25, 10, 5, 60);
    static final String COMMIT_DIFF_LAYER = "COMMIT_DIFF";

    private final AnalysisExecutionDiagnosticRepository diagnosticRepository;
    private final InsightRepository insightRepository;
    private final ObjectMapper objectMapper;
    private final RepositoryContextService repositoryContextService;
    private final int maximumPromotedCommitDiffCandidates;

    public KnowledgeSelectionServiceImpl(
            AnalysisExecutionDiagnosticRepository diagnosticRepository,
            InsightRepository insightRepository,
            ObjectMapper objectMapper,
            RepositoryContextService repositoryContextService,
            @Value("${devlog.analysis.commit-diff-promotion.max-items:15}") int maximumPromotedCommitDiffCandidates
    ) {
        this.diagnosticRepository = diagnosticRepository;
        this.insightRepository = insightRepository;
        this.objectMapper = objectMapper;
        this.repositoryContextService = repositoryContextService;
        this.maximumPromotedCommitDiffCandidates = maximumPromotedCommitDiffCandidates;
    }

    @Override
    public SelectedKnowledge select(AnalysisContext context, IntentDefinition intent,
                                    UserGuidance guidance) {
        requireMandatoryKnowledge(context, intent);
        if (intent.outputProposalType() == com.hopeful117.devlogai.proposal.entity.ProposalType.ENGINEERING_EVENT
                && context.evolutionContext() == null) {
            throw new IllegalStateException("Evolution context is required for Engineering Event Intent");
        }
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

        List<AnalysisContext.ObservationSnapshot> rankedObservations = context.observations().stream()
                .sorted(observationOrder).distinct().toList();
        List<AnalysisContext.FactSnapshot> rankedFacts = context.facts().stream()
                .sorted(factOrder)
                .toList();
        SelectionSlice selectionSlice = selectGroundingConsistentKnowledge(
                rankedObservations, rankedFacts, factOrder);
        List<AnalysisContext.ObservationSnapshot> observations = selectionSlice.observations();
        List<AnalysisContext.FactSnapshot> facts = selectionSlice.facts();
        List<Insight> insightCandidates = insightRepository
                .findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(
                        context.project().id(), List.of(InsightStatus.ACTIVE)).stream()
                .sorted(Comparator.comparing(Insight::getCreatedAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Insight::getId))
                .toList();
        List<SelectedKnowledge.InsightSnapshot> insights = insightCandidates.stream()
                .limit(BUDGET.maximumInsights()).map(this::toInsight).toList();
        List<SelectedKnowledge.ExistingArchitectureKnowledgeSnapshot> existingArchitectureKnowledge =
                selectExistingArchitectureKnowledge(intent, insightCandidates);
        var engineeringEvents = context.validatedEngineeringEvents().stream().limit(10).toList();
        var humanContextInputs = context.humanContextInputs().stream().limit(5).toList();
        var knowledgeRelations = context.knowledgeRelations();
        List<RepositoryEvidence> promotedCommitDiff = promoteCommitDiffCandidates(
                context, intent, guidance, insightCandidates);
        RepositoryContext repositoryContext = repositoryContextService.build(
                context, intent, guidance, insightCandidates, promotedCommitDiff);
        AnalysisExecutionDiagnostic diagnostic = diagnosticRepository.findById(context.analysis().id())
                .orElseThrow(() -> new IllegalStateException(
                        "Mandatory analysis diagnostics are unavailable"));
        SelectedKnowledge.DiagnosticSnapshot diagnostics = new SelectedKnowledge.DiagnosticSnapshot(
                diagnostic.isCollectionComplete(), diagnostic.isTruncated(),
                diagnostic.getWarningCount(), diagnostic.getErrorCount());
        int candidates = context.observations().size() + context.facts().size()
                + insightCandidates.size() + context.validatedEngineeringEvents().size()
                + context.knowledgeRelations().size() + repositoryContext.candidateCount();
        int selected = 1 + observations.size() + facts.size() + insights.size()
                + existingArchitectureKnowledge.size()
                + engineeringEvents.size() + humanContextInputs.size()
                + knowledgeRelations.size() + repositoryContext.evidence().size() + 1
                + (context.evolutionContext() == null ? 0 : 1);
        var metadata = new SelectedKnowledge.SelectionMetadata(
                VERSION,
                List.of("REPOSITORY_FIRST_LAYERING", "INTENT_SPECIFIC_RANKING",
                        "USER_GUIDANCE_KEYWORD_BOOST", "STABLE_TYPE_AND_ID_ORDER",
                        "DUPLICATE_FACT_CONTENT_ELIMINATION", "OBSERVATION_FACT_CLOSURE",
                        "KNOWLEDGE_BUDGET", "EVOLUTION_CONTEXT_REQUIRED",
                        "KNOWLEDGE_RELATION_PRESERVATION"),
                selected, Math.max(0, candidates + 2 - selected), BUDGET,
                diagnostic.isCollectionComplete() ? "COMPLETE" : "PARTIAL");
        String digest = digest(context, new DigestComponents(observations, facts, diagnostics,
                insights, existingArchitectureKnowledge, engineeringEvents, knowledgeRelations,
                repositoryContext, metadata));
        return new SelectedKnowledge(context.project(), context.analysis(), context.projectProfile(),
                observations, facts, diagnostics, insights, existingArchitectureKnowledge,
                engineeringEvents, humanContextInputs, knowledgeRelations, repositoryContext,
                context.evolutionContext(), metadata, digest);
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
            return containsAny(type, "ARCHITECTURE", "COMMUNICATION", CONTAINER, "MODULE") ? 100 : 10;
        if (intentId.equals("generate-readme"))
            return containsAny(type, "DOCUMENTATION", "APPLICATION", "TEST", CONTAINER) ? 100 : 20;
        return containsAny(type, "ARCHITECTURE", "APPLICATION", "TECHNOLOGY", CONTAINER) ? 80 : 40;
    }

    private int factScore(String intentId, AnalysisContext.FactSnapshot value) {
        String type = value.type().name();
        if (intentId.equals("analyze-engineering-event"))
            return containsAny(type, "COMMIT_DIFF_SUMMARY", "COMMIT_CHANGES_MODULE",
                    "COMMIT_ADDS_FEATURE", "COMMIT_FIXES_BUG", "COMMIT_REFACTORS_CODE") ? 100 : 10;
        if (intentId.equals("architecture-overview"))
            return containsAny(type, "SPRING", DOCKER, "REST", BUILD, "MODULE") ? 100 : 10;
        if (intentId.equals("generate-readme"))
            return containsAny(type, "README", "DOCUMENTATION", BUILD, DOCKER, "JAVA_VERSION") ? 100 : 20;
        return containsAny(type, "REPOSITORY", BUILD, "SPRING", DOCKER, "README") ? 80 : 40;
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

    private SelectionSlice selectGroundingConsistentKnowledge(
            List<AnalysisContext.ObservationSnapshot> rankedObservations,
            List<AnalysisContext.FactSnapshot> rankedFacts,
            Comparator<AnalysisContext.FactSnapshot> factOrder
    ) {
        Map<UUID, AnalysisContext.FactSnapshot> factsById = new LinkedHashMap<>();
        for (AnalysisContext.FactSnapshot fact : rankedFacts) {
            factsById.putIfAbsent(fact.id(), fact);
        }

        // Phase 1: budget-constrained selection (existing logic)
        List<AnalysisContext.ObservationSnapshot> observations = new ArrayList<>(rankedObservations.stream()
                .limit(BUDGET.maximumObservations())
                .toList());
        while (!observations.isEmpty()
                && requiredFactIdsFor(observations, factsById.keySet()).size() > BUDGET.maximumFacts()) {
            observations.removeLast();
        }

        // Phase 2: grounding-closure enforcement (NEW)
        // Ensure every selected observation's supportingFactIds ⊆ selectedFacts.ids
        // If budget pressure cannot satisfy closure, remove observations until it does.
        // Required facts must never be removed while the observation depending on them remains selected.
        boolean closureAchieved = false;
        int maxIterations = Math.max(observations.size(), 1);
        for (int iteration = 0; iteration < maxIterations && !closureAchieved; iteration++) {
            LinkedHashSet<UUID> currentRequiredFactIds = requiredFactIdsFor(observations, factsById.keySet());
            List<AnalysisContext.FactSnapshot> currentRequiredFacts = currentRequiredFactIds.stream()
                    .map(factsById::get)
                    .filter(Objects::nonNull)
                    .sorted(factOrder)
                    .toList();

            // Check: are all selected observations' supportingFactIds covered by the current fact set?
            boolean closureOk = observations.stream().allMatch(
                    observation -> observation.supportingFactIds().stream()
                            .allMatch(currentRequiredFactIds::contains)
            );

            if (closureOk) {
                closureAchieved = true;
                break;
            }

            // Closure failed: remove the lowest-priority observation and recompute
            if (!observations.isEmpty()) {
                observations.removeLast();
            } else {
                // No observations left; break with empty selection
                break;
            }
        }

        // Re-compute required facts and discretionary facts after closure phase
        LinkedHashSet<UUID> finalRequiredFactIds = requiredFactIdsFor(observations, factsById.keySet());
        List<AnalysisContext.FactSnapshot> finalRequiredFacts = finalRequiredFactIds.stream()
                .map(factsById::get)
                .filter(Objects::nonNull)
                .sorted(factOrder)
                .toList();

        Set<String> usedFactContentKeys = new HashSet<>();
        finalRequiredFacts.forEach(fact -> usedFactContentKeys.add(factContentKey(fact)));
        List<AnalysisContext.FactSnapshot> discretionaryFacts = rankedFacts.stream()
                .filter(fact -> !finalRequiredFactIds.contains(fact.id()))
                .filter(distinctByKey(this::factContentKey))
                .filter(fact -> usedFactContentKeys.add(factContentKey(fact)))
                .limit(Math.max(0, BUDGET.maximumFacts() - finalRequiredFacts.size()))
                .toList();

        List<AnalysisContext.FactSnapshot> facts = new ArrayList<>(finalRequiredFacts.size() + discretionaryFacts.size());
        facts.addAll(finalRequiredFacts);
        facts.addAll(discretionaryFacts);
        return new SelectionSlice(List.copyOf(observations), List.copyOf(facts));
    }

    private LinkedHashSet<UUID> requiredFactIdsFor(
            List<AnalysisContext.ObservationSnapshot> observations,
            Set<UUID> availableFactIds
    ) {
        LinkedHashSet<UUID> requiredFactIds = new LinkedHashSet<>();
        for (AnalysisContext.ObservationSnapshot observation : observations) {
            observation.supportingFactIds().stream()
                    .filter(availableFactIds::contains)
                    .forEach(requiredFactIds::add);
        }
        return requiredFactIds;
    }

    private String factContentKey(AnalysisContext.FactSnapshot fact) {
        return fact.type() + "\u0000" + fact.content();
    }

    private SelectedKnowledge.InsightSnapshot toInsight(Insight insight) {
        return new SelectedKnowledge.InsightSnapshot(insight.getId(), insight.getAnalysis().getId(),
                insight.getType(), insight.getSeverity(), insight.getTitle(), insight.getContent());
    }

    private List<SelectedKnowledge.ExistingArchitectureKnowledgeSnapshot> selectExistingArchitectureKnowledge(
            IntentDefinition intent,
            List<Insight> insightCandidates
    ) {
        if (!"architecture-overview".equals(intent.id())) {
            return List.of();
        }
        return insightCandidates.stream()
                .filter(this::isArchitectureRelevantInsight)
                .limit(BUDGET.maximumArchitectureKnowledge())
                .map(this::toExistingArchitectureKnowledge)
                .toList();
    }

    private boolean isArchitectureRelevantInsight(Insight insight) {
        if (ARCHITECTURE_SOURCE_TYPES.contains(insight.getSourceType())) {
            return true;
        }
        return insight.getSourceType() == null
                && (insight.getType() == com.hopeful117.devlogai.insight.entity.InsightType.ARCHITECTURAL
                || insight.getType() == com.hopeful117.devlogai.insight.entity.InsightType.TECHNOLOGY);
    }

    private SelectedKnowledge.ExistingArchitectureKnowledgeSnapshot toExistingArchitectureKnowledge(
            Insight insight
    ) {
        return new SelectedKnowledge.ExistingArchitectureKnowledgeSnapshot(
                insight.getId(),
                insight.getProposal().getId(),
                insight.getType(),
                insight.getSeverity(),
                insight.getSourceType(),
                insight.getTitle(),
                insight.getContent(),
                insight.getRationale(),
                insight.getEvidenceReferences(),
                insight.getCreatedAt()
        );
    }

    /**
     * Analysis consumer-specific composition (ADR-063): retrieves pre-composition
     * candidates via the shared retrieval primitive, filters for per-file
     * COMMIT_DIFF evidence, deduplicates by reference, and bounds the result
     * deterministically.
     */
    private List<RepositoryEvidence> promoteCommitDiffCandidates(
            AnalysisContext context,
            IntentDefinition intent,
            UserGuidance guidance,
            List<Insight> insightCandidates
    ) {
        List<RepositoryEvidence> allCandidates = repositoryContextService.retrieveCandidates(
                context, intent, guidance, insightCandidates);
        Set<String> seenReferences = new LinkedHashSet<>();
        List<RepositoryEvidence> promoted = new ArrayList<>();
        for (RepositoryEvidence candidate : allCandidates) {
            if (promoted.size() >= maximumPromotedCommitDiffCandidates) break;
            if (candidate.layer() != RepositoryContextLayer.COMMIT_DIFF) continue;
            if (!seenReferences.add(candidate.reference())) continue;
            promoted.add(candidate);
        }
        return promoted;
    }

    private String digest(AnalysisContext context, DigestComponents selected) {
        record DigestInput(Object project, Object analysis, Object profile, Object selectedObservations,
                           Object selectedFacts, Object diagnostic, Object selectedInsights,
                           Object existingArchitectureKnowledge,
                           Object selectedEngineeringEvents,
                           Object knowledgeRelations,
                           Object repositoryContext, Object evolutionContext, Object selectionMetadata) { }
        byte[] serialized = objectMapper.writeValueAsString(new DigestInput(
                context.project(), context.analysis(), context.projectProfile(), selected.observations(),
                selected.facts(), selected.diagnostics(), selected.insights(),
                selected.existingArchitectureKnowledge(), selected.engineeringEvents(),
                selected.knowledgeRelations(), selected.repositoryContext(), context.evolutionContext(),
                selected.metadata()))
                .getBytes(StandardCharsets.UTF_8);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(serialized));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private record DigestComponents(
            List<AnalysisContext.ObservationSnapshot> observations,
            List<AnalysisContext.FactSnapshot> facts,
            SelectedKnowledge.DiagnosticSnapshot diagnostics,
            List<SelectedKnowledge.InsightSnapshot> insights,
            List<SelectedKnowledge.ExistingArchitectureKnowledgeSnapshot> existingArchitectureKnowledge,
            List<com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot.EngineeringEventSnapshot>
                    engineeringEvents,
            List<com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot.KnowledgeRelationSnapshot>
                    knowledgeRelations,
            RepositoryContext repositoryContext,
            SelectedKnowledge.SelectionMetadata metadata) { }

    private record SelectionSlice(
            List<AnalysisContext.ObservationSnapshot> observations,
            List<AnalysisContext.FactSnapshot> facts) { }
}
