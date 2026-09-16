package com.hopeful117.devlogai.storycontextanalysis.usecase;

import com.hopeful117.devlogai.ai.reference.*;
import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.contracts.engineeringcontext.EvidenceRef;
import com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TaskSnapshotEvidenceResolverTest {
    private static final String REFERENCE = "document:00000000-0000-0000-0000-000000000001:docs/decision.md@rev-1";

    private final TaskSnapshotEvidenceResolver resolver = new TaskSnapshotEvidenceResolver();

    @Test
    void resolvesLineRangeAgainstSelectedSnapshotAndBindsCoreDigest() {
        StoryContextAnalysisResult.CausalAssessment assessment = assessment(
                new StoryContextAnalysisResult.EvidenceLocator(
                        StoryContextAnalysisResult.EvidenceLocator.LocatorKind.LINE_RANGE, 2, 3, null),
                null);

        var bound = resolver.bind(assessment, task());

        assertEquals("Decision\nThis is the authoritative relationship.",
                bound.evidenceAssertions().getFirst().resolvedContent());
        assertNotNull(bound.evidenceAssertions().getFirst().resolvedContentDigest());
    }

    @Test
    void resolvesSectionAndRejectsFabricatedExcerpt() {
        var locator = new StoryContextAnalysisResult.EvidenceLocator(
                StoryContextAnalysisResult.EvidenceLocator.LocatorKind.SECTION, null, null, "Decision");
        var assessment = assessment(locator, "invented");

        assertThrows(IllegalStateException.class, () -> resolver.bind(assessment, task()));
    }

    @Test
    void rejectsUnknownReferenceBeforeResolvingContent() {
        var assertion = new StoryContextAnalysisResult.EvidenceAssertion(
                new EvidenceRef("unknown", null, EvidenceRef.CausalEvidenceRole.NON_CAUSAL_CONTEXT),
                new StoryContextAnalysisResult.EvidenceLocator(
                        StoryContextAnalysisResult.EvidenceLocator.LocatorKind.LINE_RANGE, 1, 1, null),
                null, null, null, EvidenceRef.CausalEvidenceRole.NON_CAUSAL_CONTEXT);
        var assessment = new StoryContextAnalysisResult.CausalAssessment(
                question(), StoryContextAnalysisResult.CausalClassification.NOT_ESTABLISHED,
                List.of(assertion), "The requested relationship is not established.");

        assertThrows(IllegalStateException.class, () -> resolver.bind(assessment, task()));
    }

    @Test
    void evidenceRemovalCannotLeaveAnAuthoritativeAssertionBehind() {
        var assertion = new StoryContextAnalysisResult.EvidenceAssertion(
                new EvidenceRef(REFERENCE, null, EvidenceRef.CausalEvidenceRole.DIRECT_RELATIONSHIP_STATEMENT),
                new StoryContextAnalysisResult.EvidenceLocator(
                        StoryContextAnalysisResult.EvidenceLocator.LocatorKind.LINE_RANGE, 2, 3, null),
                null, null, null, EvidenceRef.CausalEvidenceRole.DIRECT_RELATIONSHIP_STATEMENT);
        var assessment = new StoryContextAnalysisResult.CausalAssessment(
                question(), StoryContextAnalysisResult.CausalClassification.EXPLICITLY_DOCUMENTED,
                List.of(assertion), "The assertion directly states the relationship.");
        AiTask reduced = task();
        reduced.setSelectedKnowledgeSnapshot(Map.of("repositoryContext", Map.of("evidence", List.of())));

        assertThrows(IllegalStateException.class, () -> resolver.bind(assessment, reduced));
    }

    private StoryContextAnalysisResult.CausalAssessment assessment(
            StoryContextAnalysisResult.EvidenceLocator locator, String excerpt) {
        var assertion = new StoryContextAnalysisResult.EvidenceAssertion(
                new EvidenceRef(REFERENCE, null, EvidenceRef.CausalEvidenceRole.NON_CAUSAL_CONTEXT),
                locator, null, null, excerpt, EvidenceRef.CausalEvidenceRole.NON_CAUSAL_CONTEXT);
        return new StoryContextAnalysisResult.CausalAssessment(
                question(), StoryContextAnalysisResult.CausalClassification.NOT_ESTABLISHED,
                List.of(assertion), "The selected context does not establish the requested relationship.");
    }

    private StoryContextAnalysisResult.CausalQuestion question() {
        return new StoryContextAnalysisResult.CausalQuestion("ADR-043", "ExecutionConfiguration", "CAUSAL", true);
    }

    private AiTask task() {
        AiReferenceRegistry registry = new AiReferenceRegistry(List.of(
                new AiReferenceBinding(
                        new AiReference(AiReferenceType.REPOSITORY_EVIDENCE, REFERENCE, AiReferenceScope.REPOSITORY),
                        "REPOSITORY_EVIDENCE", REFERENCE, Set.of("EVIDENCE_REFERENCE"))));
        Map<String, Object> evidence = Map.of(
                "reference", REFERENCE,
                "content", Map.of(
                        "status", "COMPLETE",
                        "text", "Introduction\nDecision\nThis is the authoritative relationship.\nConclusion",
                        "revision", "rev-1"));
        Map<String, Object> snapshot = Map.of(
                "repositoryContext", Map.of("evidence", List.of(evidence)));
        return AiTask.builder()
                .selectedKnowledgeSnapshot(snapshot)
                .aiReferenceMappingSnapshot(AiReferenceMappingSnapshot.from(registry).asMap())
                .build();
    }
}
