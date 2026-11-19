package com.hopeful117.devlogai.storycontextanalysis.usecase;

import com.hopeful117.devlogai.contracts.engineeringcontext.EvidenceRef;
import com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StoryContextResultValidatorTest {
    private static final EvidenceRef EVIDENCE = new EvidenceRef("docker-compose.yml", "docker-compose.yml");

    @Test
    void acceptsGroundedImplementationPreparation() {
        var preparation = new StoryContextAnalysisResult.ImplementationPreparation(
                List.of(new StoryContextAnalysisResult.ImplementationFile(
                        "docker-compose.yml", "Owns the service dependency", List.of(EVIDENCE))),
                List.of(),
                List.of(new StoryContextAnalysisResult.ImplementationTestPlanItem(
                        "compose configuration", "Verify the declared dependency", List.of(EVIDENCE))));

        assertDoesNotThrow(() -> StoryContextResultValidator.validateImplementationPreparation(
                preparation, Set.of("docker-compose.yml")));
    }

    @Test
    void acceptsGroundedObjectiveUnderstanding() {
        var grounding = new StoryContextAnalysisResult.GroundingMetadata(
                List.of(EVIDENCE), "FACTUAL_EXTRACTION", true,
                StoryContextAnalysisResult.RelationType.EXPLICIT);
        var objective = new StoryContextAnalysisResult.ObjectiveUnderstanding(
                "The deployment declares the service dependency", List.of(), List.of(), List.of(), grounding);

        assertDoesNotThrow(() -> StoryContextResultValidator.validateObjectiveUnderstanding(
                objective, Set.of("docker-compose.yml")));
    }

    @Test
    void rejectsUngroundedObjectiveUnderstanding() {
        var grounding = new StoryContextAnalysisResult.GroundingMetadata(
                List.of(), "FACTUAL_EXTRACTION", false, null);
        var objective = new StoryContextAnalysisResult.ObjectiveUnderstanding(
                "The deployment declares the service dependency", List.of(), List.of(), List.of(), grounding);

        assertThrows(IllegalStateException.class, () -> StoryContextResultValidator.validateObjectiveUnderstanding(
                objective, Set.of("docker-compose.yml")));
    }

    @Test
    void rejectsObjectiveSummaryWithoutGroundingMetadata() {
        var objective = new StoryContextAnalysisResult.ObjectiveUnderstanding(
                "The deployment declares the service dependency", List.of(), List.of(), List.of(), null);

        assertThrows(IllegalStateException.class, () -> StoryContextResultValidator.validateObjectiveUnderstanding(
                objective, Set.of("docker-compose.yml")));
    }

    @Test
    void rejectsUnauthorizedObjectiveUnderstandingEvidence() {
        var grounding = new StoryContextAnalysisResult.GroundingMetadata(
                List.of(EVIDENCE), "FACTUAL_EXTRACTION", true,
                StoryContextAnalysisResult.RelationType.EXPLICIT);
        var objective = new StoryContextAnalysisResult.ObjectiveUnderstanding(
                "The deployment declares the service dependency", List.of(), List.of(), List.of(), grounding);

        assertThrows(IllegalStateException.class, () -> StoryContextResultValidator.validateObjectiveUnderstanding(
                objective, Set.of("other.yml")));
    }

    @Test
    void rejectsUngroundedImplementationPreparation() {
        var preparation = new StoryContextAnalysisResult.ImplementationPreparation(
                List.of(new StoryContextAnalysisResult.ImplementationFile(
                        "docker-compose.yml", "Inferred without evidence", List.of())),
                List.of(), List.of());

        assertThrows(IllegalStateException.class, () -> StoryContextResultValidator.validateImplementationPreparation(
                preparation, Set.of("docker-compose.yml")));
    }

    @Test
    void rejectsUnauthorizedImplementationPreparationEvidence() {
        var preparation = new StoryContextAnalysisResult.ImplementationPreparation(
                List.of(new StoryContextAnalysisResult.ImplementationFile(
                        "docker-compose.yml", "Uses an unauthorized reference", List.of(EVIDENCE))),
                List.of(), List.of());

        assertThrows(IllegalStateException.class, () -> StoryContextResultValidator.validateImplementationPreparation(
                preparation, Set.of("other-file.yml")));
    }

    @Test
    void rejectsEvidenceThatOnlyContainsTheAffectedPathAsASubstring() {
        var preparation = new StoryContextAnalysisResult.ImplementationPreparation(
                List.of(new StoryContextAnalysisResult.ImplementationFile(
                        "docker-compose.yml", "Uses a similarly named resource",
                        List.of(new EvidenceRef("docker-compose.yml.bak", "devlog://files/docker-compose.yml.bak")))),
                List.of(), List.of());

        assertThrows(IllegalStateException.class, () -> StoryContextResultValidator.validateImplementationPreparation(
                preparation, Set.of("docker-compose.yml.bak")));
    }

    @Test
    void acceptsAffectedPathWhenTheAuthorizedContractCarriesTheResource() {
        var preparation = new StoryContextAnalysisResult.ImplementationPreparation(
                List.of(new StoryContextAnalysisResult.ImplementationFile(
                         "mcp-server/src/main/java/Tool.java", "Evidence-backed file",
                        List.of(new EvidenceRef("evidence-1", null)))),
                List.of(), List.of());

        assertDoesNotThrow(() -> StoryContextResultValidator.validateImplementationPreparation(
                preparation, Set.of("evidence-1"),
                Map.of("evidence-1", "mcp-server/src/main/java/Tool.java")));
    }
}
