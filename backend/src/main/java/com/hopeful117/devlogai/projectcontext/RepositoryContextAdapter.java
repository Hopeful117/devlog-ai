package com.hopeful117.devlogai.projectcontext;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
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
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.intelligence.IntentTerms;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    private final ProjectContextProvider projectContextProvider;
    private final RepositoryContextService repositoryContextService;
    private final InsightRepository insightRepository;
    private final FactRepository factRepository;
    private final ObservationRepository observationRepository;

    public RepositoryContext buildRepositoryContext(
            UUID projectId, String storyDescription) {
        ProjectContextSnapshot snapshot = projectContextProvider.build(projectId);

        return buildRepositoryContext(projectId, storyDescription, snapshot);
    }

    public RepositoryContext buildRepositoryContext(
            UUID projectId,
            String storyDescription,
            ProjectContextSnapshot snapshot
    ) {

        AnalysisContext syntheticContext =
                synthesizeAnalysisContext(projectId, snapshot, storyDescription);

        IntentDefinition intent = createIntentDefinition(storyDescription);

        List<Insight> validatedInsights =
                insightRepository.findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(
                        projectId, List.of(InsightStatus.ACTIVE));

        UserGuidance guidance = createGuidance(storyDescription);

        return repositoryContextService.build(
                syntheticContext, intent, guidance, validatedInsights);
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
}
