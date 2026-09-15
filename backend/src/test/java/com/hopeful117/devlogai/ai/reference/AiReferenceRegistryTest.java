package com.hopeful117.devlogai.ai.reference;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.hopeful117.devlogai.knowledge.relation.entity.EntityType;
import com.hopeful117.devlogai.knowledge.selection.SelectedKnowledge;
import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.projectcontext.EngineeringRelationship;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiReferenceRegistryTest {
    @Test
    void duplicateEntityRegistrationReusesOneBinding() {
        AiReferenceBinding binding = binding(AiReferenceType.INSIGHT, AiReferenceScope.PROJECT,
                "insight:1", "I1", Set.of());

        AiReferenceRegistry registry = new AiReferenceRegistry(List.of(binding, binding));

        assertThat(registry.bindings()).containsExactly(binding);
        assertThat(registry.referenceFor(AiReferenceType.INSIGHT, AiReferenceScope.PROJECT,
                "insight:1")).contains(binding.reference());
    }

    @Test
    void conflictingRegistrationFailsDeterministically() {
        AiReferenceBinding first = binding(AiReferenceType.INSIGHT, AiReferenceScope.PROJECT,
                "insight:1", "I1", Set.of());
        AiReferenceBinding second = binding(AiReferenceType.INSIGHT, AiReferenceScope.PROJECT,
                "insight:1", "I2", Set.of());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AiReferenceRegistry(List.of(first, second)))
                .withMessageContaining("conflicting AI reference binding");
    }

    @Test
    void groundingCandidatesAreCapabilityIsolated() {
        AiReferenceBinding fact = binding(AiReferenceType.FACT, AiReferenceScope.ANALYSIS_CONTEXT,
                "fact:1", "F001", Set.of("SUPPORTING_FACT"));
        AiReferenceBinding observation = binding(AiReferenceType.OBSERVATION,
                AiReferenceScope.ANALYSIS_CONTEXT, "observation:1", "O001",
                Set.of("SUPPORTING_OBSERVATION"));
        AiReferenceBinding evidence = binding(AiReferenceType.REPOSITORY_EVIDENCE,
                AiReferenceScope.REPOSITORY, "file:src/Main.java", "file:src/Main.java",
                Set.of("EVIDENCE_REFERENCE"));
        AiReferenceBinding insight = binding(AiReferenceType.INSIGHT, AiReferenceScope.PROJECT,
                "insight:1", "insight:1", Set.of());

        AiReferenceRegistry registry = new AiReferenceRegistry(List.of(fact, observation, evidence, insight));

        assertThat(registry.groundingCandidates("SUPPORTING_FACT")).containsExactly(fact);
        assertThat(registry.groundingCandidates("SUPPORTING_OBSERVATION")).containsExactly(observation);
        assertThat(registry.groundingCandidates("EVIDENCE_REFERENCE")).containsExactly(evidence);
        assertThat(registry.groundingCandidates("SUPPORTING_FACT")).doesNotContain(insight);
    }

    @Test
    void digestIsIndependentOfBindingInputOrder() {
        AiReferenceBinding first = binding(AiReferenceType.FACT, AiReferenceScope.ANALYSIS_CONTEXT,
                "fact:1", "F001", Set.of("SUPPORTING_FACT"));
        AiReferenceBinding second = binding(AiReferenceType.OBSERVATION,
                AiReferenceScope.ANALYSIS_CONTEXT, "observation:1", "O001",
                Set.of("SUPPORTING_OBSERVATION"));

        assertThat(new AiReferenceRegistry(List.of(first, second)).mappingDigest())
                .isEqualTo(new AiReferenceRegistry(List.of(second, first)).mappingDigest());
    }

    @Test
    void digestChangesWhenReferenceMappingChanges() {
        AiReferenceBinding first = binding(AiReferenceType.FACT, AiReferenceScope.ANALYSIS_CONTEXT,
                "fact:1", "F001", Set.of("SUPPORTING_FACT"));
        AiReferenceBinding changed = binding(AiReferenceType.FACT, AiReferenceScope.ANALYSIS_CONTEXT,
                "fact:1", "F002", Set.of("SUPPORTING_FACT"));

        assertThat(new AiReferenceRegistry(List.of(first)).mappingDigest())
                .isNotEqualTo(new AiReferenceRegistry(List.of(changed)).mappingDigest());
    }

    @Test
    void digestSeparatesDelimiterContainingIdentityComponents() {
        AiReferenceBinding first = binding(AiReferenceType.INSIGHT, AiReferenceScope.PROJECT,
                "a|b", "c", Set.of());
        AiReferenceBinding second = binding(AiReferenceType.INSIGHT, AiReferenceScope.PROJECT,
                "a", "b|c", Set.of());

        assertThat(new AiReferenceRegistry(List.of(first)).mappingDigest())
                .isNotEqualTo(new AiReferenceRegistry(List.of(second)).mappingDigest());
    }

    @Test
    void relationshipEndpointResolvesTheExistingBinding() {
        UUID insightId = UUID.randomUUID();
        AiReferenceBinding insight = binding(AiReferenceType.INSIGHT, AiReferenceScope.PROJECT,
                insightId.toString(), "insight:" + insightId, Set.of());
        AiReferenceRegistry registry = new AiReferenceRegistry(List.of(insight));

        EngineeringRelationship.Endpoint endpoint = new EngineeringRelationship.KnowledgeEndpoint(
                EntityType.INSIGHT, insightId);

        assertThat(registry.referenceFor(endpoint)).contains(insight.reference());
    }

    @Test
    void factoryUsesRootAnalysisAndAliasesArchitectureKnowledgeToInsight() {
        UUID projectId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        UUID insightId = UUID.randomUUID();
        SelectedKnowledge knowledge = mock(SelectedKnowledge.class);
        when(knowledge.project()).thenReturn(new AnalysisContext.ProjectSnapshot(
                projectId, "project", "project", null, null));
        when(knowledge.analysis()).thenReturn(new AnalysisContext.AnalysisSnapshot(
                analysisId, null, "intent", "v1", null, null, null, null));
        when(knowledge.projectProfile()).thenReturn(null);
        when(knowledge.selectedFacts()).thenReturn(List.of());
        when(knowledge.selectedObservations()).thenReturn(List.of());
        when(knowledge.selectedInsights()).thenReturn(List.of());
        when(knowledge.existingArchitectureKnowledge()).thenReturn(List.of(
                new SelectedKnowledge.ExistingArchitectureKnowledgeSnapshot(
                        insightId, null, null, null, null, "Architecture", "content",
                        null, List.of(), null)));
        when(knowledge.selectedEngineeringEvents()).thenReturn(List.of());
        when(knowledge.selectedHumanContextInputs()).thenReturn(List.of());
        when(knowledge.repositoryContext()).thenReturn(null);

        AiReferenceRegistry registry = AiReferenceRegistryFactory.create(knowledge);

        assertThat(registry.referenceFor(AiReferenceType.ANALYSIS,
                AiReferenceScope.ANALYSIS_CONTEXT, analysisId.toString()))
                .hasValue(new AiReference(AiReferenceType.ANALYSIS, "A001",
                        AiReferenceScope.ANALYSIS_CONTEXT));
        assertThat(registry.bindings().stream()
                .filter(binding -> binding.reference().type() == AiReferenceType.INSIGHT)
                .count()).isEqualTo(1);
    }

    private AiReferenceBinding binding(AiReferenceType type, AiReferenceScope scope,
            String identity, String reference, Set<String> capabilities) {
        return new AiReferenceBinding(new AiReference(type, reference, scope),
                type.name(), identity, capabilities);
    }
}
