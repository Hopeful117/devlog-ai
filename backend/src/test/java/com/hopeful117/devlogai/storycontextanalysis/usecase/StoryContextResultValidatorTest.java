package com.hopeful117.devlogai.storycontextanalysis.usecase;

import com.hopeful117.devlogai.contracts.engineeringcontext.EvidenceRef;
import com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResult;
import org.junit.jupiter.api.Test;

import java.util.List;
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
}
