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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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

        AnalysisContext syntheticContext =
                synthesizeAnalysisContext(projectId, snapshot, storyDescription);

        IntentDefinition intent = createIntentDefinition(storyDescription);

        List<Insight> validatedInsights =
                insightRepository.findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(
                        projectId, List.of(InsightStatus.ACTIVE));

        UserGuidance guidance = createGuidance(storyDescription);

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
            String storyDescription) {

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
                boundedFacts(snapshot, storyDescription),
                boundedObservations(snapshot, storyDescription),
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
                snapshot.engineeringStories());
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
        List<String> terms = IntentTerms.extract(storyDescription);
        List<Fact> window = factRepository.findByAnalysisIdOrderByDetectedAtDescIdDesc(
                analysisId, org.springframework.data.domain.PageRequest.of(0, FACT_WINDOW));
        record Scored(Fact fact, long matches) { }
        List<Scored> scored = new ArrayList<>();
        for (Fact fact : window) {
            long matches = IntentTerms.matches(terms, fact.getContent());
            if (matches > 0) scored.add(new Scored(fact, matches));
        }
        scored.sort(Comparator.comparingLong(Scored::matches).reversed()
                .thenComparing(scoredEntry -> scoredEntry.fact().getDetectedAt(),
                        Comparator.nullsLast(Comparator.reverseOrder())));
        return scored.stream()
                .limit(MAXIMUM_FACT_CANDIDATES)
                .map(value -> new AnalysisContext.FactSnapshot(value.fact().getId(),
                        value.fact().getType(), value.fact().getContent(),
                        value.fact().getSource(),
                        List.copyOf(value.fact().getEvidenceReferences()),
                        value.fact().getDetectedAt()))
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
        List<String> terms = IntentTerms.extract(storyDescription);
        List<Observation> window =
                observationRepository.findByAnalysisIdOrderByCreatedAtDescIdDesc(
                        analysisId, org.springframework.data.domain.PageRequest.of(
                                0, OBSERVATION_WINDOW));
        record Scored(Observation observation, long matches) { }
        List<Scored> scored = new ArrayList<>();
        for (Observation observation : window) {
            long matches = IntentTerms.matches(terms, observation.getContent());
            if (matches > 0) scored.add(new Scored(observation, matches));
        }
        scored.sort(Comparator.comparingLong(Scored::matches).reversed()
                .thenComparing(scoredEntry -> scoredEntry.observation().getCreatedAt(),
                        Comparator.nullsLast(Comparator.reverseOrder())));
        return scored.stream()
                .limit(MAXIMUM_OBSERVATION_CANDIDATES)
                .map(value -> new AnalysisContext.ObservationSnapshot(
                        value.observation().getId(), value.observation().getType(),
                        value.observation().getContent(), null, null,
                        value.observation().getSupportingFacts() == null ? List.of()
                                : value.observation().getSupportingFacts().stream()
                                        .map(fact -> fact.getId()).toList(),
                        value.observation().getCreatedAt()))
                .toList();
    }

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