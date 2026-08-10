package com.hopeful117.devlogai.projectcontext;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.insight.repository.InsightRepository;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.intent.model.UserGuidance;
import com.hopeful117.devlogai.project.entity.ProjectStatus;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
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

    private final ProjectContextProvider projectContextProvider;
    private final RepositoryContextService repositoryContextService;
    private final InsightRepository insightRepository;

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

        AnalysisContext syntheticContext = synthesizeAnalysisContext(projectId, snapshot);

        IntentDefinition intent = createIntentDefinition(storyDescription);

        List<Insight> validatedInsights =
                insightRepository.findByProjectIdOrderByCreatedAtDesc(projectId);

        UserGuidance guidance = createGuidance(storyDescription);

        return repositoryContextService.build(
                syntheticContext, intent, guidance, validatedInsights);
    }

    private AnalysisContext synthesizeAnalysisContext(
            UUID projectId,
            ProjectContextSnapshot snapshot) {

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
                List.of(),
                List.of(),
                snapshot.recentKnowledgeEvents(),
                snapshot.recentAnalyses(),
                snapshot.architectureArtifacts(),
                snapshot.relatedDecisions(),
                snapshot.recentMilestones(),
                snapshot.validatedProposals(),
                null,
                snapshot.validatedEngineeringEvents(),
                snapshot.openChallenges(),
                snapshot.knowledgeRelations());
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
