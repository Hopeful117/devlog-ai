package com.hopeful117.devlogai.ai.engine.service;

import com.hopeful117.devlogai.ai.engine.dto.AiProposalResult;
import com.hopeful117.devlogai.ai.engine.dto.AnalysisSynthesisResult;
import com.hopeful117.devlogai.ai.engine.exception.InvalidAiTaskResultException;
import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.intent.service.IntentCatalog;
import com.hopeful117.devlogai.proposal.entity.ProposalType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AiProposalContractValidatorTest {
    private final AiProposalContractValidator validator =
            new AiProposalContractValidator(new IntentCatalog());

    @Test
    void validatesSynthesisGroundingAgainstSelectedKnowledge() {
        String reference = "src/main/java/App.java:42";
        UUID factId = UUID.randomUUID();
        UUID humanContextId = UUID.randomUUID();
        AiTask task = AiTask.builder()
                .selectedKnowledgeSnapshot(Map.of(
                        "selectedFacts", List.of(Map.of(
                                "id", factId.toString(),
                                "evidenceReferences", List.of(reference))),
                        "humanContext", List.of(Map.of("id", humanContextId.toString()))))
                .build();
        AnalysisSynthesisResult grounded = new AnalysisSynthesisResult(
                "Architecture",
                List.of(new AnalysisSynthesisResult.SynthesisSection("Components", "REST API")),
                AnalysisSynthesisResult.ArchitectureDeltaConclusion.NO_MATERIAL_DELTA,
                List.of(reference));
        AnalysisSynthesisResult foreign = new AnalysisSynthesisResult(
                grounded.title(), grounded.sections(), grounded.deltaConclusion(),
                List.of("invented.java:1"));

        assertDoesNotThrow(() -> validator.validateSynthesis(task, grounded, false));
        AnalysisSynthesisResult groundedById = new AnalysisSynthesisResult(
                grounded.title(), grounded.sections(), grounded.deltaConclusion(),
                List.of(factId.toString()));
        assertDoesNotThrow(() -> validator.validateSynthesis(task, groundedById, false));
        AnalysisSynthesisResult groundedByHumanContext = new AnalysisSynthesisResult(
                grounded.title(), grounded.sections(), grounded.deltaConclusion(),
                List.of(humanContextId.toString()));
        assertDoesNotThrow(() -> validator.validateSynthesis(task, groundedByHumanContext, false));
        assertThrows(InvalidAiTaskResultException.class,
                () -> validator.validateSynthesis(task, foreign, false));
    }

    @Test
    void requiresDeltaForRelationshipAbsentFromExistingArchitectureKnowledge() {
        AiTask task = AiTask.builder().selectedKnowledgeSnapshot(Map.of(
                "selectedFacts", List.of(Map.of(
                        "id", UUID.randomUUID().toString(),
                        "content", "from=api,to=database")),
                "existingArchitectureKnowledge", List.of(Map.of(
                        "title", "Containerized project",
                        "content", "The project uses Docker Compose."))))
                .build();
        AnalysisSynthesisResult synthesis = new AnalysisSynthesisResult(
                "Architecture",
                List.of(new AnalysisSynthesisResult.SynthesisSection(
                        "Dependencies", "The API depends on the database.")),
                AnalysisSynthesisResult.ArchitectureDeltaConclusion.NO_MATERIAL_DELTA,
                List.of());

        assertThrows(InvalidAiTaskResultException.class,
                () -> validator.validateSynthesis(task, synthesis, false));
        assertDoesNotThrow(() -> validator.validateSynthesis(task, synthesis, true));
    }

    @Test
    void acceptsGroundedEventAndRejectsDuplicateOrForeignGrounding() {
        UUID factId = UUID.randomUUID();
        UUID observationId = UUID.randomUUID();
        String reference = "git:source:target:file";
        AiTask task = AiTask.builder().intentId("analyze-engineering-event").intentVersion("v1")
                .selectedKnowledgeSnapshot(Map.of(
                        "selectedFacts", List.of(Map.of("id", factId.toString(),
                                "evidenceReferences", List.of(reference))),
                        "selectedObservations", List.of(Map.of("id", observationId.toString()))))
                .build();
        AiProposalResult proposal = new AiProposalResult(ProposalType.ENGINEERING_EVENT,
                Map.of("schemaVersion", "engineering-event-proposal-v1",
                        "category", "BUG_RESOLUTION", "title", "Fix retry",
                        "summary", "Retry behavior was corrected.",
                        "significance", "Failures no longer leave partial state."),
                new BigDecimal("0.9000"), List.of(factId), List.of(observationId), List.of(reference));

        assertDoesNotThrow(() -> validator.validate(task, List.of(proposal)));
        List<AiProposalResult> duplicates = List.of(proposal, proposal);
        assertThrows(InvalidAiTaskResultException.class,
                () -> validator.validate(task, duplicates));

        AiProposalResult foreign = new AiProposalResult(ProposalType.ENGINEERING_EVENT,
                proposal.payload(), proposal.confidence(), List.of(UUID.randomUUID()),
                List.of(), List.of(reference));
        List<AiProposalResult> foreignGrounding = List.of(foreign);
        assertThrows(InvalidAiTaskResultException.class,
                () -> validator.validate(task, foreignGrounding));

        AiProposalResult wrongType = new AiProposalResult(ProposalType.INSIGHT,
                Map.of("title", "Wrong"), BigDecimal.ONE, List.of(), List.of(), List.of());
        List<AiProposalResult> wrongOutput = List.of(wrongType);
        assertThrows(InvalidAiTaskResultException.class,
                () -> validator.validate(task, wrongOutput));

        AiProposalResult malformed = new AiProposalResult(ProposalType.ENGINEERING_EVENT,
                Map.of("schemaVersion", "wrong", "category", "UNKNOWN", "title", "Bad",
                        "summary", "Bad", "significance", "Bad"),
                BigDecimal.ONE, List.of(), List.of(), List.of(reference));
        List<AiProposalResult> malformedOutput = List.of(malformed);
        assertThrows(InvalidAiTaskResultException.class,
                () -> validator.validate(task, malformedOutput));

        AiProposalResult invalidCategory = eventProposal("UNKNOWN", "Bad category", reference);
        List<AiProposalResult> invalidCategoryOutput = List.of(invalidCategory);
        assertThrows(InvalidAiTaskResultException.class,
                () -> validator.validate(task, invalidCategoryOutput));

        AiProposalResult blankTitle = eventProposal("BUG_RESOLUTION", " ", reference);
        List<AiProposalResult> blankTitleOutput = List.of(blankTitle);
        assertThrows(InvalidAiTaskResultException.class,
                () -> validator.validate(task, blankTitleOutput));

        AiProposalResult ungrounded = new AiProposalResult(ProposalType.ENGINEERING_EVENT,
                eventProposal("BUG_RESOLUTION", "Ungrounded", reference).payload(),
                BigDecimal.ONE, List.of(), List.of(), List.of());
        List<AiProposalResult> ungroundedOutput = List.of(ungrounded);
        assertThrows(InvalidAiTaskResultException.class,
                () -> validator.validate(task, ungroundedOutput));
    }

    @Test
    void validatesArchitectureInsightDeltaAgainstSelectedExistingKnowledge() {
        UUID factId = UUID.randomUUID();
        UUID observationId = UUID.randomUUID();
        UUID targetInsightId = UUID.randomUUID();
        String reference = "src/main/java/App.java:42";
        AiTask task = AiTask.builder()
                .intentId("architecture-overview")
                .intentVersion("v1")
                .selectedKnowledgeSnapshot(Map.of(
                        "selectedFacts", List.of(Map.of("id", factId.toString(),
                                "evidenceReferences", List.of(reference))),
                        "selectedObservations", List.of(Map.of("id", observationId.toString())),
                        "existingArchitectureKnowledge", List.of(Map.of(
                                "insightId", targetInsightId.toString()
                        ))))
                .build();
        AiProposalResult enriches = new AiProposalResult(ProposalType.INSIGHT,
                Map.of("insightType", "ARCHITECTURE_DESCRIPTION",
                        "title", "Boundary enrichment",
                        "summary", "Modules also isolate deployment cadence.",
                        "rationale", "New evidence shows teams deploy independently.",
                        "deltaType", "ENRICHES",
                        "targetInsightId", targetInsightId.toString()),
                new BigDecimal("0.9500"), List.of(factId), List.of(observationId), List.of(reference));
        AiProposalResult invalidTarget = new AiProposalResult(ProposalType.INSIGHT,
                Map.of("insightType", "ARCHITECTURE_DESCRIPTION",
                        "title", "Boundary enrichment",
                        "summary", "Modules also isolate deployment cadence.",
                        "rationale", "New evidence shows teams deploy independently.",
                        "deltaType", "ENRICHES",
                        "targetInsightId", UUID.randomUUID().toString()),
                new BigDecimal("0.9500"), List.of(factId), List.of(observationId), List.of(reference));
        AiProposalResult newInsight = new AiProposalResult(ProposalType.INSIGHT,
                Map.of("insightType", "TECHNOLOGY_DESCRIPTION",
                        "title", "Deployment stack",
                        "summary", "The project uses containerized delivery.",
                        "rationale", "Container evidence is newly grounded.",
                        "deltaType", "NEW"),
                new BigDecimal("0.9100"), List.of(factId), List.of(observationId), List.of(reference));

        assertDoesNotThrow(() -> validator.validate(task, List.of(enriches, newInsight)));
        assertThrows(InvalidAiTaskResultException.class,
                () -> validator.validate(task, List.of(invalidTarget)));
    }

    @Test
    void rejectsMissingDeltaTypeForArchitectureOverviewV2AtCoreBoundary() {
        UUID factId = UUID.randomUUID();
        UUID observationId = UUID.randomUUID();
        String reference = "docker-compose.yml";
        AiTask task = AiTask.builder()
                .intentId("architecture-overview")
                .intentVersion("v2")
                .selectedKnowledgeSnapshot(Map.of(
                        "selectedFacts", List.of(Map.of(
                                "id", factId.toString(),
                                "evidenceReferences", List.of(reference))),
                        "selectedObservations", List.of(Map.of(
                                "id", observationId.toString())),
                        "existingArchitectureKnowledge", List.of()))
                .build();
        AiProposalResult missingDeltaType = new AiProposalResult(
                ProposalType.INSIGHT,
                Map.of(
                        "insightType", "ARCHITECTURE_DESCRIPTION",
                        "title", "Explicit backend dependency",
                        "summary", "The backend depends on the AI engine.",
                        "rationale", "Docker Compose declares the dependency."),
                new BigDecimal("0.9500"),
                List.of(factId),
                List.of(observationId),
                List.of(reference));

        assertThrows(InvalidAiTaskResultException.class,
                () -> validator.validate(task, List.of(missingDeltaType)));
    }

    @Test
    void rejectsSupportingFactIdWhenItMatchesSelectedInsightInsteadOfSelectedFact() {
        UUID factId = UUID.randomUUID();
        UUID observationId = UUID.randomUUID();
        UUID strayInsightId = UUID.randomUUID();
        String reference = "src/main/java/App.java:42";
        AiTask task = AiTask.builder()
                .intentId("describe-project")
                .intentVersion("v1")
                .selectedKnowledgeSnapshot(Map.of(
                        "selectedFacts", List.of(Map.of("id", factId.toString(),
                                "evidenceReferences", List.of(reference))),
                        "selectedObservations", List.of(Map.of("id", observationId.toString())),
                        "selectedInsights", List.of(Map.of(
                                "id", strayInsightId.toString(),
                                "title", "Controllers"
                        ))))
                .build();
        AiProposalResult proposal = new AiProposalResult(ProposalType.INSIGHT,
                Map.of("insightType", "PROJECT_PRESENTATION",
                        "title", "Presentation",
                        "summary", "The project exposes controllers.",
                        "rationale", "Grounding is incorrect.",
                        "deltaType", "NEW"),
                new BigDecimal("0.9000"),
                List.of(strayInsightId),
                List.of(observationId),
                List.of(reference));

        assertThrows(InvalidAiTaskResultException.class,
                () -> validator.validate(task, List.of(proposal)));
    }

    @Test
    void rejectsSupportingObservationIdWhenItMatchesSelectedInsightInsteadOfSelectedObservation() {
        UUID factId = UUID.randomUUID();
        UUID observationId = UUID.randomUUID();
        UUID strayInsightId = UUID.randomUUID();
        String reference = "src/main/java/App.java:42";
        AiTask task = AiTask.builder()
                .intentId("describe-project")
                .intentVersion("v1")
                .selectedKnowledgeSnapshot(Map.of(
                        "selectedFacts", List.of(Map.of("id", factId.toString(),
                                "evidenceReferences", List.of(reference))),
                        "selectedObservations", List.of(Map.of("id", observationId.toString())),
                        "selectedInsights", List.of(Map.of(
                                "id", strayInsightId.toString(),
                                "title", "Controllers"
                        ))))
                .build();
        AiProposalResult proposal = new AiProposalResult(ProposalType.INSIGHT,
                Map.of("insightType", "PROJECT_PRESENTATION",
                        "title", "Presentation",
                        "summary", "The project exposes controllers.",
                        "rationale", "Grounding is incorrect.",
                        "deltaType", "NEW"),
                new BigDecimal("0.9000"),
                List.of(factId),
                List.of(strayInsightId),
                List.of(reference));

        assertThrows(InvalidAiTaskResultException.class,
                () -> validator.validate(task, List.of(proposal)));
    }

    private AiProposalResult eventProposal(String category, String title, String reference) {
        return new AiProposalResult(ProposalType.ENGINEERING_EVENT,
                Map.of("schemaVersion", "engineering-event-proposal-v1", "category", category,
                        "title", title, "summary", "Summary", "significance", "Significance"),
                BigDecimal.ONE, List.of(), List.of(), List.of(reference));
    }

    @Test
    void endpointReferencesDoNotCoverDirectionalRelationship() {
        AiTask task = relationshipTask(List.of(Map.of(
                "title", "Containerized project",
                "content", "The project uses Docker Compose.",
                "evidenceReferences", List.of(
                        "backend/Dockerfile",
                        "ai-engine/Dockerfile"))));

        assertThrows(InvalidAiTaskResultException.class,
                () -> validator.validateSynthesis(task, noDeltaSynthesis(), false));
    }

    @Test
    void reverseRelationshipDoesNotCoverSelectedDirection() {
        AiTask task = relationshipTask(List.of(Map.of(
                "title", "Reverse dependency",
                "content", "ai-engine -> backend")));

        assertThrows(InvalidAiTaskResultException.class,
                () -> validator.validateSynthesis(task, noDeltaSynthesis(), false));
    }

    @Test
    void exactDirectionalRelationshipCoversSelectedEdge() {
        AiTask task = relationshipTask(List.of(Map.of(
                "title", "Backend dependency",
                "content", "backend -> ai-engine")));

        assertDoesNotThrow(() -> validator.validateSynthesis(task, noDeltaSynthesis(), false));
    }

    private AiTask relationshipTask(List<Map<String, Object>> existingKnowledge) {
        return AiTask.builder().selectedKnowledgeSnapshot(Map.of(
                "selectedFacts", List.of(Map.of(
                        "id", UUID.randomUUID().toString(),
                        "content", "from=backend,to=ai-engine")),
                "existingArchitectureKnowledge", existingKnowledge))
                .build();
    }

    private AnalysisSynthesisResult noDeltaSynthesis() {
        return new AnalysisSynthesisResult(
                "Architecture",
                List.of(new AnalysisSynthesisResult.SynthesisSection(
                        "Dependencies", "The backend depends on the AI engine.")),
                AnalysisSynthesisResult.ArchitectureDeltaConclusion.NO_MATERIAL_DELTA,
                List.of());
    }
}
