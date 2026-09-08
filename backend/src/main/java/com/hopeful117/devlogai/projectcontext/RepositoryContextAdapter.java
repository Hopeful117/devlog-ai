package com.hopeful117.devlogai.projectcontext;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.history.entity.CommitParent;
import com.hopeful117.devlogai.history.entity.ProjectCommit;
import com.hopeful117.devlogai.history.repository.ProjectCommitRepository;
import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.insight.entity.InsightStatus;
import com.hopeful117.devlogai.insight.repository.InsightRepository;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.intent.model.UserGuidance;
import com.hopeful117.devlogai.fact.entity.Fact;
import com.hopeful117.devlogai.fact.repository.FactRepository;
import com.hopeful117.devlogai.observation.entity.Observation;
import com.hopeful117.devlogai.observation.repository.ObservationRepository;
import com.hopeful117.devlogai.project.entity.ProjectStatus;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessService;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessSummary;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import com.hopeful117.devlogai.repositorycontext.intelligence.IntentTerms;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bridges {@link ProjectContextProvider} to {@link RepositoryContextEngine} for
 * Engineering Story preparation without requiring a persisted Analysis.
 *
 * <p>This adapter synthesizes an {@link AnalysisContext} from a
 * {@link ProjectContextSnapshot}, creates a local {@link IntentDefinition}
 * for the {@code engineering-story-v1} context profile, and calls
 * {@link RepositoryContextService#build} directly — bypassing
 * {@code KnowledgeSelectionServiceImpl} which requires a persisted Analysis.</p>
 */
@Service
@RequiredArgsConstructor
public class RepositoryContextAdapter {

    private static final String ENGINEERING_STORY_PREPARATION =
            "engineering-story-preparation";

    private static final int FACT_WINDOW = 200;
    private static final int OBSERVATION_WINDOW = 200;
    private static final int MAXIMUM_FACT_CANDIDATES = 8;
    private static final int MAXIMUM_OBSERVATION_CANDIDATES = 6;

    private static final Pattern GIT_COMMIT_REFERENCE =
            Pattern.compile("^git:[0-9a-fA-F\\-]+:([0-9a-fA-F]{40}|[0-9a-fA-F]{64})$");
    private static final Pattern DIFF_REFERENCE =
            Pattern.compile("^diff:([0-9a-fA-F]{40}|[0-9a-fA-F]{64}):");

    private final ProjectContextProvider projectContextProvider;
    private final RepositoryContextService repositoryContextService;
    private final InsightRepository insightRepository;
    private final FactRepository factRepository;
    private final ObservationRepository observationRepository;
    private final ProjectCommitRepository commitRepository;
    private final ProjectFreshnessService freshnessService;

    public RepositoryContext buildRepositoryContext(
            UUID projectId, String storyDescription) {
        ProjectContextSnapshot snapshot = projectContextProvider.build(projectId);

        return buildRepositoryContext(projectId, storyDescription, snapshot, List.of(), null);
    }

    public RepositoryContext buildRepositoryContext(
            UUID projectId,
            String storyDescription,
            ProjectContextSnapshot snapshot
    ) {
        return buildRepositoryContext(projectId, storyDescription, snapshot, List.of(), null);
    }

    public RepositoryContext buildRepositoryContext(
            UUID projectId,
            String storyDescription,
            ProjectContextSnapshot snapshot,
            List<String> files,
            UUID storyId
    ) {
        var currentStory = resolveStory(snapshot, storyId);
        String selectionText = selectionText(storyDescription, currentStory, files);
        BoundedKnowledge boundedKnowledge = boundedKnowledge(
                snapshot, storyDescription, currentStory, files);
        AnalysisContext syntheticContext =
                synthesizeAnalysisContext(projectId, snapshot, boundedKnowledge, currentStory);

        IntentDefinition intent = createIntentDefinition(selectionText);

        List<Insight> validatedInsights =
                insightRepository.findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(
                        projectId, List.of(InsightStatus.ACTIVE));

        UserGuidance guidance = createGuidance(selectionText);

        RepositoryContext context = repositoryContextService.build(
                syntheticContext, intent, guidance, validatedInsights);

        if (storyId != null) {
            context = filterByStoryScope(context, projectId, snapshot, storyId);
        }

        return context;
    }

    private AnalysisContext synthesizeAnalysisContext(
            UUID projectId,
            ProjectContextSnapshot snapshot,
            BoundedKnowledge boundedKnowledge,
            ProjectContextSnapshot.EngineeringStorySnapshot currentStory) {

        AnalysisContext.ProjectSnapshot projectSnapshot =
                new AnalysisContext.ProjectSnapshot(
                        projectId,
                        snapshot.project().name(),
                        snapshot.project().slug(),
                        null,
                        ProjectStatus.ACTIVE);

        UUID syntheticAnalysisId = UUID.nameUUIDFromBytes(
                projectId.toString().getBytes());

        AnalysisContext.AnalysisSnapshot analysisSnapshot =
                new AnalysisContext.AnalysisSnapshot(
                        syntheticAnalysisId,
                        AnalysisType.ARCHITECTURE_REVIEW,
                        ENGINEERING_STORY_PREPARATION,
                        "v1",
                        AnalysisStatus.COMPLETED,
                        Instant.now(),
                        null,
                        Instant.now());

        return new AnalysisContext(
                projectSnapshot,
                analysisSnapshot,
                snapshot.latestProjectProfile(),
                boundedKnowledge.facts(),
                boundedKnowledge.observations(),
                snapshot.recentKnowledgeEvents(),
                snapshot.recentAnalyses(),
                snapshot.architectureArtifacts(),
                snapshot.relatedDecisions(),
                snapshot.recentMilestones(),
                snapshot.validatedProposals(),
                null,
                snapshot.validatedEngineeringEvents(),
                snapshot.openChallenges(),
                snapshot.knowledgeRelations(),
                currentStory == null ? snapshot.engineeringStories() : List.of(currentStory));
    }

    private ProjectContextSnapshot.EngineeringStorySnapshot resolveStory(
            ProjectContextSnapshot snapshot,
            UUID storyId
    ) {
        if (storyId == null) return null;
        return snapshot.engineeringStories().stream()
                .filter(story -> storyId.equals(story.id()))
                .findFirst()
                .orElse(null);
    }

    private String selectionText(
            String storyDescription,
            ProjectContextSnapshot.EngineeringStorySnapshot currentStory,
            List<String> files
    ) {
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, storyDescription);
        if (currentStory != null) {
            addIfPresent(parts, currentStory.title());
            addIfPresent(parts, currentStory.storyPath());
        }
        if (files != null) {
            files.forEach(file -> addIfPresent(parts, file));
        }
        return String.join(" ", parts);
    }

    private void addIfPresent(List<String> parts, String value) {
        if (value != null && !value.isBlank()) parts.add(value);
    }

    /**
     * Bounded deterministic retrieval of recent Facts from the latest
     * comparable baseline Analysis (ADR-063: large persisted collections are
     * bounded BEFORE the candidate pool). Relevant items are chosen by intent-
     * term overlap over a fixed recent window; identity, provenance and time
     * are preserved verbatim. No baseline profile means no candidates.
     */
    List<AnalysisContext.FactSnapshot> boundedFacts(
            ProjectContextSnapshot snapshot, String storyDescription) {
        if (snapshot.latestProjectProfile() == null
                || snapshot.latestProjectProfile().analysisId() == null) {
            return List.of();
        }
        UUID analysisId = snapshot.latestProjectProfile().analysisId();
        List<ScoredFact> scored = rankedFacts(
                analysisId, List.of(), IntentTerms.extract(storyDescription));
        return scored.stream()
                .filter(ScoredFact::relevant)
                .limit(MAXIMUM_FACT_CANDIDATES)
                .map(value -> toFactSnapshot(value.fact()))
                .toList();
    }

    /** Bounded Observation counterpart of {@link #boundedFacts}. */
    List<AnalysisContext.ObservationSnapshot> boundedObservations(
            ProjectContextSnapshot snapshot, String storyDescription) {
        if (snapshot.latestProjectProfile() == null
                || snapshot.latestProjectProfile().analysisId() == null) {
            return List.of();
        }
        UUID analysisId = snapshot.latestProjectProfile().analysisId();
        List<ScoredObservation> scored = rankedObservations(
                analysisId, List.of(), IntentTerms.extract(storyDescription));
        return scored.stream()
                .filter(ScoredObservation::relevant)
                .limit(MAXIMUM_OBSERVATION_CANDIDATES)
                .map(value -> toObservationSnapshot(value.observation()))
                .toList();
    }

    private BoundedKnowledge boundedKnowledge(
            ProjectContextSnapshot snapshot,
            String intentText,
            ProjectContextSnapshot.EngineeringStorySnapshot currentStory,
            List<String> files
    ) {
        if (snapshot.latestProjectProfile() == null
                || snapshot.latestProjectProfile().analysisId() == null) {
            return new BoundedKnowledge(List.of(), List.of());
        }
        UUID analysisId = snapshot.latestProjectProfile().analysisId();
        List<String> storyTerms = IntentTerms.extract(selectionText(null, currentStory, files));
        List<String> intentTerms = IntentTerms.extract(intentText);
        List<ScoredFact> facts = rankedFacts(analysisId, storyTerms, intentTerms);
        List<ScoredObservation> observations = rankedObservations(
                analysisId, storyTerms, intentTerms);
        Map<UUID, ScoredFact> factsById = new HashMap<>();
        facts.forEach(value -> factsById.putIfAbsent(value.fact().getId(), value));

        List<ScoredObservation> selectedObservations = new ArrayList<>();
        LinkedHashSet<UUID> requiredFactIds = new LinkedHashSet<>();
        for (ScoredObservation candidate : observations) {
            if (!candidate.relevant()
                    || selectedObservations.size() >= MAXIMUM_OBSERVATION_CANDIDATES) {
                continue;
            }
            List<UUID> supportingIds = supportingFactIds(candidate.observation());
            if (!factsById.keySet().containsAll(supportingIds)) continue;
            LinkedHashSet<UUID> expanded = new LinkedHashSet<>(requiredFactIds);
            expanded.addAll(supportingIds);
            if (expanded.size() > MAXIMUM_FACT_CANDIDATES) continue;
            requiredFactIds = expanded;
            selectedObservations.add(candidate);
        }

        List<ScoredFact> selectedFacts = requiredFactIds.stream()
                .map(factsById::get)
                .filter(Objects::nonNull)
                .sorted(factOrder())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        Set<UUID> selectedFactIds = new HashSet<>(requiredFactIds);
        for (ScoredFact candidate : facts) {
            if (selectedFacts.size() >= MAXIMUM_FACT_CANDIDATES) break;
            if (candidate.relevant() && selectedFactIds.add(candidate.fact().getId())) {
                selectedFacts.add(candidate);
            }
        }
        return new BoundedKnowledge(
                selectedFacts.stream().map(value -> toFactSnapshot(value.fact())).toList(),
                selectedObservations.stream()
                        .map(value -> toObservationSnapshot(value.observation())).toList());
    }

    private List<ScoredFact> rankedFacts(
            UUID analysisId,
            List<String> storyTerms,
            List<String> intentTerms
    ) {
        return factRepository.findByAnalysisIdOrderByDetectedAtDescIdDesc(
                        analysisId, org.springframework.data.domain.PageRequest.of(0, FACT_WINDOW))
                .stream()
                .map(fact -> new ScoredFact(fact,
                        IntentTerms.matches(storyTerms, factSearchText(fact)),
                        IntentTerms.matches(intentTerms, factSearchText(fact))))
                .sorted(factOrder())
                .toList();
    }

    private List<ScoredObservation> rankedObservations(
            UUID analysisId,
            List<String> storyTerms,
            List<String> intentTerms
    ) {
        return observationRepository.findByAnalysisIdOrderByCreatedAtDescIdDesc(
                        analysisId, org.springframework.data.domain.PageRequest.of(
                                0, OBSERVATION_WINDOW))
                .stream()
                .map(observation -> new ScoredObservation(observation,
                        IntentTerms.matches(storyTerms, observationSearchText(observation)),
                        IntentTerms.matches(intentTerms, observationSearchText(observation))))
                .sorted(observationOrder())
                .toList();
    }

    private String factSearchText(Fact fact) {
        return String.join(" ",
                Objects.toString(fact.getType(), ""),
                Objects.toString(fact.getContent(), ""),
                Objects.toString(fact.getSource(), ""),
                joinValues(fact.getEvidenceReferences()));
    }

    private String observationSearchText(Observation observation) {
        return Objects.toString(observation.getType(), "") + " "
                + Objects.toString(observation.getContent(), "");
    }

    private String joinValues(Iterable<?> values) {
        if (values == null) return "";
        List<String> normalized = new ArrayList<>();
        values.forEach(value -> {
            if (value != null) normalized.add(value.toString());
        });
        normalized.sort(String::compareTo);
        return String.join(" ", normalized);
    }

    private Comparator<ScoredFact> factOrder() {
        return Comparator.comparingLong(ScoredFact::storyMatches).reversed()
                .thenComparing(Comparator.comparingLong(ScoredFact::intentMatches).reversed())
                .thenComparing(value -> value.fact().getDetectedAt(),
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(value -> value.fact().getId());
    }

    private Comparator<ScoredObservation> observationOrder() {
        return Comparator.comparingLong(ScoredObservation::storyMatches).reversed()
                .thenComparing(Comparator.comparingLong(
                        ScoredObservation::intentMatches).reversed())
                .thenComparing(value -> value.observation().getCreatedAt(),
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(value -> value.observation().getId());
    }

    private List<UUID> supportingFactIds(Observation observation) {
        if (observation.getSupportingFacts() == null) return List.of();
        return observation.getSupportingFacts().stream()
                .map(Fact::getId)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(UUID::toString))
                .toList();
    }

    private AnalysisContext.FactSnapshot toFactSnapshot(Fact fact) {
        List<String> references = fact.getEvidenceReferences() == null ? List.of()
                : fact.getEvidenceReferences().stream().sorted().toList();
        return new AnalysisContext.FactSnapshot(fact.getId(), fact.getType(), fact.getContent(),
                fact.getSource(), references, fact.getDetectedAt());
    }

    private AnalysisContext.ObservationSnapshot toObservationSnapshot(Observation observation) {
        return new AnalysisContext.ObservationSnapshot(
                observation.getId(), observation.getType(), observation.getContent(),
                observation.getRuleId(), observation.getRuleVersion(),
                supportingFactIds(observation), observation.getCreatedAt());
    }

    private record ScoredFact(Fact fact, long storyMatches, long intentMatches) {
        boolean relevant() {
            return storyMatches > 0 || intentMatches > 0;
        }
    }

    private record ScoredObservation(
            Observation observation,
            long storyMatches,
            long intentMatches
    ) {
        boolean relevant() {
            return storyMatches > 0 || intentMatches > 0;
        }
    }

    private record BoundedKnowledge(
            List<AnalysisContext.FactSnapshot> facts,
            List<AnalysisContext.ObservationSnapshot> observations
    ) { }

    private IntentDefinition createIntentDefinition(String storyDescription) {
        String objective = (storyDescription != null && !storyDescription.isBlank())
                ? storyDescription
                : "Engineering Story preparation";

        return new IntentDefinition(
                ENGINEERING_STORY_PREPARATION,
                "v1",
                objective,
                List.of(),
                List.of("deterministic evidence only"),
                Map.of(),
                "engineering-story-context-v1",
                List.of("engineering-story-v1"));
    }

    private UserGuidance createGuidance(String storyDescription) {
        if (storyDescription == null || storyDescription.isBlank()) {
            return null;
        }
        return new UserGuidance(
                storyDescription,
                "kiko",
                "focused",
                "analytical",
                ENGINEERING_STORY_PREPARATION,
                List.of());
    }

    /**
     * Filters RepositoryContext by story commit window using authoritative
     * repository history. Implements all Story 0111 scoping rules.
     */
    RepositoryContext filterByStoryScope(
            RepositoryContext context,
            UUID projectId,
            ProjectContextSnapshot snapshot,
            UUID storyId
    ) {
        var story = snapshot.engineeringStories().stream()
                .filter(s -> storyId.equals(s.id()))
                .findFirst()
                .orElse(null);

        if (story == null) {
            return filterToNonTechnical(context);
        }

        String baseCommit = story.baseCommit();
        String targetCommit = story.targetCommit();

        if (baseCommit == null && targetCommit == null) {
            return filterToNonTechnical(context);
        }

        if (baseCommit == null || targetCommit == null) {
            return filterBaseOnly(context, projectId, baseCommit, targetCommit);
        }

        Set<String> window = findCommitsInWindow(projectId, baseCommit, targetCommit);
        if (window.isEmpty()) {
            return filterToNonTechnical(context);
        }

        return filterContextByCommitSet(context, window);
    }

    private RepositoryContext filterBaseOnly(
            RepositoryContext context,
            UUID projectId,
            String baseCommit,
            String targetCommit
    ) {
        String snapshotRevision = resolveDeterministicSnapshotRevision(projectId);
        if (snapshotRevision == null) {
            return filterToNonTechnical(context);
        }
        String upperBound = targetCommit != null ? targetCommit : snapshotRevision;
        Set<String> window = findCommitsInWindow(projectId, baseCommit, upperBound);
        if (window.isEmpty()) {
            return filterToNonTechnical(context);
        }
        return filterContextByCommitSet(context, window);
    }

    private String resolveDeterministicSnapshotRevision(UUID projectId) {
        ProjectFreshnessSummary summary = freshnessService.summary(projectId);
        for (var row : summary.checkedSources()) {
            if (row.baseline() != null && row.baseline().analyzedRevision() != null) {
                return row.baseline().analyzedRevision();
            }
        }
        for (var row : summary.checkedSources()) {
            if (row.source().ingestedRevision() != null) {
                return row.source().ingestedRevision();
            }
        }
        return null;
    }

    private RepositoryContext filterToNonTechnical(RepositoryContext context) {
        List<RepositoryEvidence> nonTechnical = context.evidence().stream()
                .filter(e -> !isTechnicalEvidence(e))
                .toList();
        return withFilteredEvidence(context, nonTechnical);
    }

    private RepositoryContext filterContextByCommitSet(
            RepositoryContext context,
            Set<String> commitHashesInWindow
    ) {
        List<RepositoryEvidence> filtered = context.evidence().stream()
                .filter(e -> {
                    if (!isTechnicalEvidence(e)) return true;
                    return evidenceRefersToCommitsInWindow(e, commitHashesInWindow);
                })
                .toList();
        return withFilteredEvidence(context, filtered);
    }

    private boolean isTechnicalEvidence(RepositoryEvidence evidence) {
        String kind = evidence.kind();
        String sourceType = evidence.provenance() != null
                ? evidence.provenance().sourceType() : null;
        if ("CORE_KNOWLEDGE".equals(sourceType)
                && (kind.equals("INSIGHT") || kind.equals("DECISION")
                    || kind.equals("ENGINEERING_EVENT"))) {
            return false;
        }
        if (kind.equals("PROJECT_NOTE") || kind.equals("MILESTONE")
                || kind.equals("ARTIFACT") || kind.equals("ENGINEERING_STORY")
                || kind.equals("CHALLENGE")) {
            return false;
        }
        if (kind.equals("ANALYSIS") || kind.equals("FRESHNESS")
                || kind.equals("DIAGNOSTIC") || kind.equals("SELECTION_METADATA")) {
            return false;
        }
        return true;
    }

    private boolean evidenceRefersToCommitsInWindow(
            RepositoryEvidence evidence,
            Set<String> commitHashesInWindow
    ) {
        String sha = extractCommitSha(evidence.reference());
        if (sha != null && commitHashesInWindow.contains(sha)) return true;

        for (String ref : evidence.relatedReferences()) {
            sha = extractCommitSha(ref);
            if (sha != null && commitHashesInWindow.contains(sha)) return true;
        }

        return false;
    }

    private String extractCommitSha(String reference) {
        if (reference == null) return null;
        Matcher gitMatcher = GIT_COMMIT_REFERENCE.matcher(reference);
        if (gitMatcher.matches()) return gitMatcher.group(1).toLowerCase();
        Matcher diffMatcher = DIFF_REFERENCE.matcher(reference);
        if (diffMatcher.matches()) return diffMatcher.group(1).toLowerCase();
        return null;
    }

    /**
     * Finds all commits in (baseCommit, targetCommit] using BFS graph
     * traversal on persisted commit-parent relationships.
     */
    private Set<String> findCommitsInWindow(
            UUID projectId,
            String baseCommitSha,
            String targetCommitSha
    ) {
        List<ProjectCommit> allCommits =
                commitRepository.findByProjectIdOrderByCommittedAtAscCommitHashAsc(projectId);

        Map<String, ProjectCommit> commitBySha = new HashMap<>();
        for (ProjectCommit commit : allCommits) {
            commitBySha.put(commit.getCommitHash().toLowerCase(), commit);
        }

        String baseLower = baseCommitSha.toLowerCase();
        String targetLower = targetCommitSha.toLowerCase();

        if (!commitBySha.containsKey(targetLower)) return Set.of();
        if (!commitBySha.containsKey(baseLower)) return Set.of();

        Set<String> ancestorsOfTarget = findAllAncestors(targetLower, commitBySha);
        if (!ancestorsOfTarget.contains(baseLower)) return Set.of();

        Set<String> ancestorsOfBase = findAllAncestors(baseLower, commitBySha);

        Set<String> window = new HashSet<>(ancestorsOfTarget);
        window.removeAll(ancestorsOfBase);
        return window;
    }

    private Set<String> findAllAncestors(
            String startSha,
            Map<String, ProjectCommit> commitBySha
    ) {
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(startSha);
        visited.add(startSha);

        while (!queue.isEmpty()) {
            String currentSha = queue.poll();
            ProjectCommit current = commitBySha.get(currentSha);
            if (current == null) continue;

            for (CommitParent parent : current.getParents()) {
                String parentSha = parent.getParentHash().toLowerCase();
                if (visited.add(parentSha)) {
                    queue.add(parentSha);
                }
            }
        }

        return visited;
    }

    private RepositoryContext withFilteredEvidence(
            RepositoryContext context,
            List<RepositoryEvidence> filteredEvidence
    ) {
        return new RepositoryContext(
                context.contextVersion(),
                context.profile(),
                context.activeProfileKeys(),
                context.contextPlanVersion(),
                context.contextIntelligenceExplanations(),
                filteredEvidence,
                context.selectedByLayer(),
                context.diagnostics(),
                context.budget(),
                context.usedTokens(),
                filteredEvidence.size(),
                context.discardedCount(),
                context.truncated(),
                context.selectionDecisions().stream()
                        .filter(d -> filteredEvidence.stream()
                                .anyMatch(e -> e.reference().equals(d.evidenceReference())))
                        .toList(),
                context.warnings(),
                context.contextDigest()
        );
    }
}
