package com.hopeful117.devlogai.ai.engine.service;

import com.hopeful117.devlogai.ai.engine.dto.AiProposalResult;
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

    private AiProposalResult eventProposal(String category, String title, String reference) {
        return new AiProposalResult(ProposalType.ENGINEERING_EVENT,
                Map.of("schemaVersion", "engineering-event-proposal-v1", "category", category,
                        "title", title, "summary", "Summary", "significance", "Significance"),
                BigDecimal.ONE, List.of(), List.of(), List.of(reference));
    }
}
