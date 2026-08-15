package com.hopeful117.devlogai.insight.service;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import com.hopeful117.devlogai.insight.entity.InsightType;
import com.hopeful117.devlogai.insight.entity.InsightStatus;
import com.hopeful117.devlogai.insight.repository.InsightRepository;
import com.hopeful117.devlogai.knowledge.relation.entity.KnowledgeRelation;
import com.hopeful117.devlogai.knowledge.relation.entity.KnowledgeRelationType;
import com.hopeful117.devlogai.knowledge.relation.repository.KnowledgeRelationRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.proposal.entity.ProposalType;
import com.hopeful117.devlogai.proposal.entity.ValidatableProposal;
import com.hopeful117.devlogai.validation.entity.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for InsightPromotionService with semantic similarity assessment.
 * 
 * These tests verify that:
 * 1. Promotion proceeds after exact duplicate guard validation
 * 2. Semantic similarity assessment is computed and returned in the result
 * 3. Non-insight proposals are gracefully handled
 * 4. Incomplete payloads are rejected before similarity assessment
 * 5. Severity validation is enforced
 * 6. Enrichment relations are created when deltaType=ENRICHES
 */
@ExtendWith(MockitoExtension.class)
class InsightPromotionServiceTest {
    @Mock InsightRepository repository;
    @Mock KnowledgeRelationRepository relations;
    @Mock InsightSimilarityService similarityService;

    @InjectMocks
    private InsightPromotionService promotionService;

    @Test
    void shouldPromoteAcceptedInsightProposalWithCompleteProvenance() {
        Project project = new Project();
        Analysis analysis = new Analysis();
        Validation validation = new Validation();
        ValidatableProposal proposal = ValidatableProposal.builder()
                .project(project)
                .analysis(analysis)
                .type(ProposalType.INSIGHT)
                .payload(Map.of(
                        "insightType", "ARCHITECTURE_DESCRIPTION",
                        "title", "Modular architecture",
                        "summary", "The application is split into bounded modules.",
                        "rationale", "Boundaries keep modules independently deployable."
                ))
                .confidence(new BigDecimal("0.9200"))
                .evidenceReferences(List.of("src/main/java/com/example/App.java"))
                .build();
        when(repository.save(any(Insight.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Configure existing active insights for the project so similarity assessment can compute a score
        Insight existingInsight = Insight.builder()
                .id(UUID.randomUUID())
                .project(project)
                .analysis(analysis)
                .type(InsightType.ARCHITECTURAL)
                .severity(InsightSeverity.INFO)
                .title("Modular architecture legacy")
                .content("The application was split into bounded modules for deployability.")
                .rationale("Legacy modularization boundaries.")
                .confidence(new BigDecimal("0.8800"))
                .sourceType("ARCHITECTURE_DESCRIPTION")
                .status(InsightStatus.ACTIVE)
                .build();
        when(repository.findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(
                project.getId(), List.of(InsightStatus.ACTIVE)))
                .thenReturn(List.of(existingInsight));
        when(similarityService.computeSimilarity(
                anyString(), anyString(), anyList()))
                .thenReturn(0.32); // Moderate similarity between new and existing

        PromotionResult result = promotionService.promote(proposal, validation, InsightSeverity.WARNING);

        assertNotNull(result);
        assertNotNull(result.getPromotedInsight());
        assertNotNull(result.getSimilarityAssessment());
        assertEquals(InsightType.ARCHITECTURAL, result.getPromotedInsight().getType());
        assertEquals(InsightSeverity.WARNING, result.getPromotedInsight().getSeverity());
        assertEquals("Modular architecture", result.getPromotedInsight().getTitle());
        assertEquals("The application is split into bounded modules.", result.getPromotedInsight().getContent());
        assertEquals("Boundaries keep modules independently deployable.", result.getPromotedInsight().getRationale());
        assertEquals(new BigDecimal("0.9200"), result.getPromotedInsight().getConfidence());
        assertEquals(List.of("src/main/java/com/example/App.java"), result.getPromotedInsight().getEvidenceReferences());
        assertEquals("ARCHITECTURE_DESCRIPTION", result.getPromotedInsight().getSourceType());

        SimilarityAssessment assessment = result.getSimilarityAssessment();
        assertNotNull(assessment);
        assertTrue(assessment.isHasClosestMatch());
        assertNotNull(assessment.getClosestInsightId());
        assertNotNull(assessment.getSimilarityScore());
        assertTrue(assessment.getSimilarityScore() >= 0.0 && assessment.getSimilarityScore() <= 1.0);

        verify(repository).save(any(Insight.class));
        verifyNoMoreInteractions(repository, relations, similarityService);
    }

    @Test
    void shouldIgnoreNonInsightProposal() {
        ValidatableProposal proposal = ValidatableProposal.builder()
                .type(ProposalType.DOCUMENTATION)
                .build();
        PromotionResult result = promotionService.promote(proposal, new Validation(), null);
        assertNull(result.getPromotedInsight());
        assertNotNull(result.getSimilarityAssessment());
        verifyNoMoreInteractions(repository, relations, similarityService);
    }

    @Test
    void shouldRejectIncompleteInsightPayload() {
        ValidatableProposal proposal = ValidatableProposal.builder()
                .type(ProposalType.INSIGHT)
                .payload(Map.of("insightType", "TECHNOLOGY_DESCRIPTION", "title", "Stack"))
                .build();
        Validation validation = new Validation();
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> promotionService.promote(proposal, validation, InsightSeverity.INFO));
        assertEquals("Accepted insight proposal is missing payload field: summary", error.getMessage());
        verifyNoMoreInteractions(repository, relations, similarityService);
    }

    @Test
    void shouldRequireHumanSeverityForInsightPromotion() {
        ValidatableProposal proposal = ValidatableProposal.builder()
                .type(ProposalType.INSIGHT)
                .payload(Map.of(
                        "insightType", "TECHNOLOGY_DESCRIPTION",
                        "title", "Stack",
                        "summary", "The project uses Spring Boot."
                ))
                .build();
        Validation validation = new Validation();
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> promotionService.promote(proposal, validation, null));
        assertEquals("Severity is required when accepting an insight proposal", error.getMessage());
        verifyNoMoreInteractions(repository, relations, similarityService);
    }

    @Test
    void shouldCreateKnowledgeRelationForAcceptedEnrichment() {
        UUID targetInsightId = java.util.UUID.randomUUID();
        Project project = Project.builder().id(java.util.UUID.randomUUID()).build();
        Analysis analysis = new Analysis();
        Validation validation = new Validation();
        ValidatableProposal proposal = ValidatableProposal.builder()
                .project(project)
                .analysis(analysis)
                .type(ProposalType.INSIGHT)
                .payload(Map.of(
                        "insightType", "ARCHITECTURE_DESCRIPTION",
                        "title", "Architecture refinement",
                        "summary", "A module boundary also isolates deployment cadence.",
                        "rationale", "New repository evidence shows deploy independence.",
                        "deltaType", "ENRICHES",
                        "targetInsightId", targetInsightId.toString()
                ))
                .build();
        Insight savedInsight = Insight.builder().id(java.util.UUID.randomUUID()).project(project).build();
        Insight targetInsight = Insight.builder().id(targetInsightId).project(project).build();
        when(repository.save(any(Insight.class))).thenReturn(savedInsight);
        when(repository.findById(targetInsightId)).thenReturn(Optional.of(targetInsight));

        PromotionResult result = promotionService.promote(proposal, validation, InsightSeverity.INFO);

        assertNotNull(result.getPromotedInsight());
        assertNotNull(result.getSimilarityAssessment());

        ArgumentCaptor<KnowledgeRelation> relationCaptor = ArgumentCaptor.forClass(KnowledgeRelation.class);
        verify(relations).save(relationCaptor.capture());
        KnowledgeRelation relation = relationCaptor.getValue();
        assertEquals(savedInsight.getId(), relation.getSourceEntityId());
        assertEquals(targetInsightId, relation.getTargetEntityId());
        assertEquals(KnowledgeRelationType.DERIVED_FROM, relation.getRelationType());
    }
}