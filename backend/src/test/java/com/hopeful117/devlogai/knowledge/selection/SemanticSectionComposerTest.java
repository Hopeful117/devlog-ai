package com.hopeful117.devlogai.knowledge.selection;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.fact.entity.FactType;
import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import com.hopeful117.devlogai.insight.entity.InsightType;
import com.hopeful117.devlogai.observation.entity.ObservationType;
import com.hopeful117.devlogai.profile.dto.ProjectProfileResponse;
import com.hopeful117.devlogai.project.entity.ProjectStatus;
import com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot;
import com.hopeful117.devlogai.projectcontextinput.entity.ProjectHumanContextInputType;
import com.hopeful117.devlogai.repositorycontext.ContextProfile;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextLayer;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import com.hopeful117.devlogai.repositorycontext.intelligence.EvidenceScore;
import com.hopeful117.devlogai.knowledge.selection.SemanticSection.SectionId;
import com.hopeful117.devlogai.knowledge.selection.SemanticSection.PromptSemanticSection;
import com.hopeful117.devlogai.knowledge.selection.SemanticSection.PromptSemanticSectionItem;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticSectionComposerTest {

    private final SemanticSectionComposer composer = new SemanticSectionComposer();

    private static final SelectedKnowledge.DiagnosticSnapshot DIAGNOSTICS =
            new SelectedKnowledge.DiagnosticSnapshot(true, false, 0, 0);
    private static final SelectedKnowledge.SelectionMetadata METADATA =
            new SelectedKnowledge.SelectionMetadata(
                    "knowledge-selection-v4", List.of(), 0, 0,
                    new SelectedKnowledge.KnowledgeBudget(40, 25, 10, 5, 60),
                    "COMPLETE"
            );

    @Test
    void shouldClassifyFactIntoExplicitSections() {
        UUID factId = UUID.randomUUID();
        SelectedKnowledge sk = selectedKnowledge(
                List.of(fact(FactType.DOCKERFILE_PRESENT, factId)),
                List.of(), List.of(), List.of(), List.of(), null);

        List<PromptSemanticSection> sections = composer.compose(sk);

        Set<String> sectionIds = sections.stream()
                .map(PromptSemanticSection::sectionId).collect(Collectors.toSet());
        assertTrue(sectionIds.contains("ARCHITECTURE"));
        assertTrue(sectionIds.contains("PROJECT_STATE"));

        List<PromptSemanticSectionItem> archItems = sections.stream()
                .filter(s -> s.sectionId().equals("ARCHITECTURE"))
                .findFirst().orElseThrow().items();
        assertTrue(archItems.stream().anyMatch(i ->
                i.itemId().equals(factId.toString()) && i.label().equals("DOCKERFILE_PRESENT")));
    }

    @Test
    void shouldClassifyAllFactTypesExplicitly() {
        for (FactType factType : FactType.values()) {
            if (factType == FactType.OTHER) continue;
            UUID factId = UUID.randomUUID();
            SelectedKnowledge sk = selectedKnowledge(
                    List.of(fact(factType, factId)),
                    List.of(), List.of(), List.of(), List.of(), null);

            List<PromptSemanticSection> sections = composer.compose(sk);
            assertFalse(sections.isEmpty(),
                    "FactType " + factType + " should be classified into at least one section");
        }
    }

    @Test
    void shouldClassifyAllObservationTypesExplicitly() {
        for (ObservationType obsType : ObservationType.values()) {
            if (obsType == ObservationType.OTHER) continue;
            UUID obsId = UUID.randomUUID();
            SelectedKnowledge sk = selectedKnowledge(
                    List.of(),
                    List.of(observation(obsType, obsId)),
                    List.of(), List.of(), List.of(), null);

            List<PromptSemanticSection> sections = composer.compose(sk);
            assertFalse(sections.isEmpty(),
                    "ObservationType " + obsType + " should be classified into at least one section");
        }
    }

    @Test
    void shouldClassifyAllInsightTypesExplicitly() {
        for (InsightType insightType : InsightType.values()) {
            UUID insightId = UUID.randomUUID();
            SelectedKnowledge sk = selectedKnowledge(
                    List.of(),
                    List.of(),
                    List.of(insight(insightId, insightType)),
                    List.of(), List.of(), null);

            List<PromptSemanticSection> sections = composer.compose(sk);
            assertFalse(sections.isEmpty(),
                    "InsightType " + insightType + " should be classified into at least one section");

            boolean hasValidatedKnowledge = sections.stream()
                    .anyMatch(s -> s.sectionId().equals("VALIDATED_KNOWLEDGE"));
            assertTrue(hasValidatedKnowledge,
                    "InsightType " + insightType + " must have VALIDATED_KNOWLEDGE membership");
        }
    }

    @Test
    void shouldClassifyAllRepositoryLayersExplicitly() {
        for (RepositoryContextLayer layer : RepositoryContextLayer.values()) {
            RepositoryContext ctx = repositoryContextWithLayer(layer);
            SelectedKnowledge sk = selectedKnowledge(
                    List.of(), List.of(), List.of(), List.of(), List.of(), ctx);

            List<PromptSemanticSection> sections = composer.compose(sk);
            boolean hasRepoChanges = sections.stream()
                    .anyMatch(s -> s.sectionId().equals("REPOSITORY_CHANGES"));
            assertTrue(hasRepoChanges,
                    "RepositoryContextLayer " + layer + " must have REPOSITORY_CHANGES membership");
        }
    }

    @Test
    void shouldClassifyAllHumanContextTypesExplicitly() {
        for (ProjectHumanContextInputType type : ProjectHumanContextInputType.values()) {
            UUID inputId = UUID.randomUUID();
            SelectedKnowledge sk = selectedKnowledge(
                    List.of(), List.of(), List.of(),
                    List.of(humanContext(inputId, type)),
                    List.of(), null);

            List<PromptSemanticSection> sections = composer.compose(sk);
            boolean hasHumanContext = sections.stream()
                    .anyMatch(s -> s.sectionId().equals("HUMAN_CONTEXT"));
            assertTrue(hasHumanContext,
                    "ProjectHumanContextInputType " + type + " must have HUMAN_CONTEXT membership");
        }
    }

    @Test
    void shouldSupportMultiMembershipForDualClassifiedTypes() {
        UUID factId = UUID.randomUUID();
        SelectedKnowledge sk = selectedKnowledge(
                List.of(fact(FactType.DOCKERFILE_PRESENT, factId)),
                List.of(), List.of(), List.of(), List.of(), null);

        List<PromptSemanticSection> sections = composer.compose(sk);

        boolean inArch = sections.stream()
                .filter(s -> s.sectionId().equals("ARCHITECTURE"))
                .flatMap(s -> s.items().stream())
                .anyMatch(i -> i.itemId().equals(factId.toString()));
        boolean inProjectState = sections.stream()
                .filter(s -> s.sectionId().equals("PROJECT_STATE"))
                .flatMap(s -> s.items().stream())
                .anyMatch(i -> i.itemId().equals(factId.toString()));
        assertTrue(inArch && inProjectState,
                "DOCKERFILE_PRESENT should be in both ARCHITECTURE and PROJECT_STATE");
    }

    @Test
    void shouldNotDuplicateContentInMultiMembership() {
        UUID factId = UUID.randomUUID();
        SelectedKnowledge sk = selectedKnowledge(
                List.of(fact(FactType.DOCKERFILE_PRESENT, factId)),
                List.of(), List.of(), List.of(), List.of(), null);

        List<PromptSemanticSection> sections = composer.compose(sk);

        long totalReferences = sections.stream()
                .flatMap(s -> s.items().stream())
                .filter(i -> i.itemId().equals(factId.toString()))
                .count();
        assertEquals(2, totalReferences,
                "Same item should appear as reference in exactly 2 sections, not duplicated content");
    }

    @Test
    void shouldOmitSectionsWithOnlyProjectIdentity() {
        SelectedKnowledge sk = selectedKnowledge(
                List.of(), List.of(), List.of(), List.of(), List.of(), null);

        List<PromptSemanticSection> sections = composer.compose(sk);

        for (PromptSemanticSection section : sections) {
            boolean onlyProjectItems = section.items().stream()
                    .allMatch(i -> i.itemType().equals("PROJECT")
                            || i.itemType().equals("ANALYSIS")
                            || i.itemType().equals("PROJECT_PROFILE"));
            if (section.sectionId().equals("PROJECT_STATE")) {
                assertTrue(onlyProjectItems,
                        "PROJECT_STATE should only contain project identity items when no other data");
            }
        }
    }

    @Test
    void shouldOmitEmptySectionForPartialData() {
        UUID factId = UUID.randomUUID();
        SelectedKnowledge sk = selectedKnowledge(
                List.of(fact(FactType.SPRING_BOOT_DETECTED, factId)),
                List.of(), List.of(), List.of(), List.of(), null);

        List<PromptSemanticSection> sections = composer.compose(sk);

        boolean hasDecisions = sections.stream()
                .anyMatch(s -> s.sectionId().equals("DECISIONS"));
        assertFalse(hasDecisions,
                "DECISIONS section should be omitted when no decision-relevant items exist");
    }

    @Test
    void shouldMaintainDeterministicSectionOrdering() {
        UUID factId1 = UUID.randomUUID();
        UUID factId2 = UUID.randomUUID();
        SelectedKnowledge sk = selectedKnowledge(
                List.of(
                        fact(FactType.DOCKERFILE_PRESENT, factId1),
                        fact(FactType.SPRING_BOOT_DETECTED, factId2)
                ),
                List.of(), List.of(), List.of(), List.of(), null);

        List<PromptSemanticSection> first = composer.compose(sk);
        List<PromptSemanticSection> second = composer.compose(sk);

        assertEquals(first, second, "Same input must produce identical output");
        assertEquals("PROJECT_STATE", first.getFirst().sectionId());
    }

    @Test
    void shouldMaintainDeterministicItemOrderingWithinSections() {
        UUID factId1 = UUID.randomUUID();
        UUID factId2 = UUID.randomUUID();
        SelectedKnowledge sk = selectedKnowledge(
                List.of(
                        fact(FactType.BUILD_MODULE_DECLARED, factId1),
                        fact(FactType.DOCKERFILE_PRESENT, factId2)
                ),
                List.of(), List.of(), List.of(), List.of(), null);

        List<PromptSemanticSection> first = composer.compose(sk);
        List<PromptSemanticSection> second = composer.compose(sk);

        assertEquals(first, second, "Same input must produce identical item ordering");
    }

    @Test
    void shouldProduceLightweightReferencesNotFullContent() {
        UUID factId = UUID.randomUUID();
        SelectedKnowledge sk = selectedKnowledge(
                List.of(fact(FactType.DOCKERFILE_PRESENT, factId)),
                List.of(), List.of(), List.of(), List.of(), null);

        List<PromptSemanticSection> sections = composer.compose(sk);

        for (PromptSemanticSection section : sections) {
            for (PromptSemanticSectionItem item : section.items()) {
                assertNotNull(item.itemType());
                assertNotNull(item.itemId());
                assertNotNull(item.label());
                assertFalse(item.itemType().isEmpty());
                assertFalse(item.itemId().isEmpty());
                assertFalse(item.label().isEmpty());
            }
        }
    }

    @Test
    void shouldClassifyMandatoryHumanContextMembership() {
        UUID inputId = UUID.randomUUID();
        SelectedKnowledge sk = selectedKnowledge(
                List.of(), List.of(), List.of(),
                List.of(humanContext(inputId, ProjectHumanContextInputType.KNOWN_GAP)),
                List.of(), null);

        List<PromptSemanticSection> sections = composer.compose(sk);

        boolean hasHumanContext = sections.stream()
                .anyMatch(s -> s.sectionId().equals("HUMAN_CONTEXT"));
        assertTrue(hasHumanContext, "KNOWN_GAP must have mandatory HUMAN_CONTEXT membership");
    }

    @Test
    void shouldClassifyMandatoryValidatedKnowledgeForInsights() {
        UUID insightId = UUID.randomUUID();
        SelectedKnowledge sk = selectedKnowledge(
                List.of(), List.of(),
                List.of(insight(insightId, InsightType.RISK)),
                List.of(), List.of(), null);

        List<PromptSemanticSection> sections = composer.compose(sk);

        boolean hasValidatedKnowledge = sections.stream()
                .anyMatch(s -> s.sectionId().equals("VALIDATED_KNOWLEDGE"));
        assertTrue(hasValidatedKnowledge, "RISK insight must have mandatory VALIDATED_KNOWLEDGE membership");
    }

    @Test
    void shouldClassifyMandatoryValidatedKnowledgeForEvents() {
        UUID eventId = UUID.randomUUID();
        SelectedKnowledge sk = selectedKnowledge(
                List.of(), List.of(), List.of(), List.of(),
                List.of(engineeringEvent(eventId, "Test Event")),
                null);

        List<PromptSemanticSection> sections = composer.compose(sk);

        boolean hasValidatedKnowledge = sections.stream()
                .anyMatch(s -> s.sectionId().equals("VALIDATED_KNOWLEDGE"));
        assertTrue(hasValidatedKnowledge, "Engineering event must have mandatory VALIDATED_KNOWLEDGE membership");
    }

    @Test
    void shouldNotInventClassificationsForUnknownTypes() {
        SelectedKnowledge sk = new SelectedKnowledge(
                null, null, null,
                List.of(), List.of(fact(FactType.OTHER, UUID.randomUUID())),
                DIAGNOSTICS, List.of(), List.of(), List.of(), List.of(), List.of(),
                null, null, METADATA, "a".repeat(64));

        List<PromptSemanticSection> sections = composer.compose(sk);

        for (PromptSemanticSection section : sections) {
            boolean hasOtherFact = section.items().stream()
                    .anyMatch(i -> i.itemType().equals("FACT") && i.label().equals("OTHER"));
            assertFalse(hasOtherFact,
                    "UNCLASSIFIED (OTHER) fact should not appear in any semantic section");
        }
    }

    @Test
    void shouldIncludeProjectIdentityInProjectState() {
        SelectedKnowledge sk = selectedKnowledge(
                List.of(), List.of(), List.of(), List.of(), List.of(), null);

        List<PromptSemanticSection> sections = composer.compose(sk);

        boolean hasProjectState = sections.stream()
                .anyMatch(s -> s.sectionId().equals("PROJECT_STATE"));
        assertTrue(hasProjectState, "Project identity should be in PROJECT_STATE");
    }

    @Test
    void shouldIncludeRepositoryEvidenceInSections() {
        RepositoryContext ctx = repositoryContextWithLayer(RepositoryContextLayer.COMMIT_DIFF);
        SelectedKnowledge sk = selectedKnowledge(
                List.of(), List.of(), List.of(), List.of(), List.of(), ctx);

        List<PromptSemanticSection> sections = composer.compose(sk);

        boolean hasRepoChanges = sections.stream()
                .anyMatch(s -> s.sectionId().equals("REPOSITORY_CHANGES"));
        boolean hasHistory = sections.stream()
                .anyMatch(s -> s.sectionId().equals("HISTORY"));
        assertTrue(hasRepoChanges, "COMMIT_DIFF evidence should be in REPOSITORY_CHANGES");
        assertTrue(hasHistory, "COMMIT_DIFF evidence should also be in HISTORY");
    }

    @Test
    void shouldHandleNullRepositoryContextGracefully() {
        SelectedKnowledge sk = selectedKnowledge(
                List.of(), List.of(), List.of(), List.of(), List.of(), null);

        List<PromptSemanticSection> sections = composer.compose(sk);
        assertNotNull(sections);
    }

    @Test
    void shouldNotExpandSelection() {
        SelectedKnowledge sk = selectedKnowledge(
                List.of(), List.of(), List.of(), List.of(), List.of(), null);

        List<PromptSemanticSection> sections = composer.compose(sk);

        for (PromptSemanticSection section : sections) {
            for (PromptSemanticSectionItem item : section.items()) {
                assertFalse(item.itemId().isEmpty());
            }
        }
    }

    private SelectedKnowledge selectedKnowledge(
            List<AnalysisContext.FactSnapshot> facts,
            List<AnalysisContext.ObservationSnapshot> observations,
            List<SelectedKnowledge.InsightSnapshot> insights,
            List<ProjectContextSnapshot.HumanContextInputSnapshot> humanContextInputs,
            List<ProjectContextSnapshot.EngineeringEventSnapshot> engineeringEvents,
            RepositoryContext repositoryContext
    ) {
        return new SelectedKnowledge(
                new AnalysisContext.ProjectSnapshot(UUID.randomUUID(), "DevLog", "devlog-ai",
                        "desc", ProjectStatus.ACTIVE),
                new AnalysisContext.AnalysisSnapshot(UUID.randomUUID(), null, null, null, null,
                        null, null, null),
                null,
                observations,
                facts,
                DIAGNOSTICS,
                insights,
                List.of(),
                engineeringEvents,
                humanContextInputs,
                List.of(),
                repositoryContext,
                null,
                METADATA,
                "a".repeat(64)
        );
    }

    private AnalysisContext.FactSnapshot fact(FactType type, UUID id) {
        return new AnalysisContext.FactSnapshot(id, type, type.name() + " content",
                "test-source", List.of(), Instant.EPOCH);
    }

    private AnalysisContext.ObservationSnapshot observation(ObservationType type, UUID id) {
        return new AnalysisContext.ObservationSnapshot(id, type, type.name() + " content",
                "rule-1", "v1", List.of(), Instant.EPOCH);
    }

    private SelectedKnowledge.InsightSnapshot insight(UUID id, InsightType type) {
        return new SelectedKnowledge.InsightSnapshot(id, UUID.randomUUID(),
                type, InsightSeverity.INFO, type.name() + " title", type.name() + " content");
    }

    private ProjectContextSnapshot.HumanContextInputSnapshot humanContext(UUID id,
            ProjectHumanContextInputType type) {
        return new ProjectContextSnapshot.HumanContextInputSnapshot(id, type,
                type.name() + " title", type.name() + " content", "ACTIVE", Instant.EPOCH);
    }

    private ProjectContextSnapshot.EngineeringEventSnapshot engineeringEvent(UUID id, String title) {
        return new ProjectContextSnapshot.EngineeringEventSnapshot(id, "CATEGORY", title,
                title + " summary", UUID.randomUUID(), "base", "target", Instant.EPOCH,
                UUID.randomUUID());
    }

    private RepositoryContext repositoryContextWithLayer(RepositoryContextLayer layer) {
        RepositoryEvidence evidence = new RepositoryEvidence(
                layer, "KIND", "reference-" + UUID.randomUUID(), "summary",
                Instant.EPOCH, EvidenceScore.unscored(), List.of(),
                new RepositoryEvidence.EvidenceProvenance("KIND", "repo", "ref", "ext"),
                Map.of(), 0, List.of(), null, null);
        return new RepositoryContext(
                "v1", ContextProfile.PROJECT_STATE, List.of(), "intel-v1", List.of(),
                List.of(evidence), Map.of(layer, 1),
                new RepositoryContext.ContextBudget(60, 500, 20, 6000),
                0, 0, 0, false, List.of(), List.of(), "d".repeat(64));
    }
}
