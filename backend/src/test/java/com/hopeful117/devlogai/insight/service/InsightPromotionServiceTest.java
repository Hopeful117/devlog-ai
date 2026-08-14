package com.hopeful117.devlogai.insight.service;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import com.hopeful117.devlogai.insight.entity.InsightTrustState;
import com.hopeful117.devlogai.insight.entity.InsightType;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InsightPromotionServiceTest {
    @Mock InsightRepository repository;
    @Mock KnowledgeRelationRepository relations;

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

        new InsightPromotionService(repository, relations).promote(proposal, validation, InsightSeverity.WARNING);

        ArgumentCaptor<Insight> captor = ArgumentCaptor.forClass(Insight.class);
        verify(repository).save(captor.capture());
        Insight insight = captor.getValue();
        assertAll(
                () -> assertSame(project, insight.getProject()),
                () -> assertSame(analysis, insight.getAnalysis()),
                () -> assertSame(proposal, insight.getProposal()),
                () -> assertSame(validation, insight.getValidation()),
                () -> assertEquals(InsightType.ARCHITECTURAL, insight.getType()),
                () -> assertEquals(InsightSeverity.WARNING, insight.getSeverity()),
                () -> assertEquals("Modular architecture", insight.getTitle()),
                () -> assertEquals("The application is split into bounded modules.", insight.getContent()),
                () -> assertEquals("Boundaries keep modules independently deployable.", insight.getRationale()),
                () -> assertEquals(new BigDecimal("0.9200"), insight.getConfidence()),
                () -> assertEquals(List.of("src/main/java/com/example/App.java"), insight.getEvidenceReferences()),
                () -> assertEquals("ARCHITECTURE_DESCRIPTION", insight.getSourceType())
        );
        verifyNoInteractions(relations);
    }

    @Test
    void shouldIgnoreNonInsightProposal() {
        ValidatableProposal proposal = ValidatableProposal.builder()
                .type(ProposalType.DOCUMENTATION)
                .build();
        new InsightPromotionService(repository, relations).promote(proposal, new Validation(), null);
        verifyNoInteractions(repository, relations);
    }

    @Test
    void shouldRejectIncompleteInsightPayload() {
        ValidatableProposal proposal = ValidatableProposal.builder()
                .type(ProposalType.INSIGHT)
                .payload(Map.of("insightType", "TECHNOLOGY_DESCRIPTION", "title", "Stack"))
                .build();
        InsightPromotionService service = new InsightPromotionService(repository, relations);
        Validation validation = new Validation();
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.promote(proposal, validation, InsightSeverity.INFO));
        assertEquals("Accepted insight proposal is missing payload field: summary", error.getMessage());
        verifyNoInteractions(repository, relations);
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
        InsightPromotionService service = new InsightPromotionService(repository, relations);
        Validation validation = new Validation();
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.promote(proposal, validation, null));
        assertEquals("Severity is required when accepting an insight proposal", error.getMessage());
        verifyNoInteractions(repository, relations);
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
        when(repository.findById(targetInsightId)).thenReturn(java.util.Optional.of(targetInsight));

        new InsightPromotionService(repository, relations).promote(proposal, validation, InsightSeverity.INFO);

        ArgumentCaptor<KnowledgeRelation> relationCaptor = ArgumentCaptor.forClass(KnowledgeRelation.class);
        verify(relations).save(relationCaptor.capture());
        KnowledgeRelation relation = relationCaptor.getValue();
        assertEquals(savedInsight.getId(), relation.getSourceEntityId());
        assertEquals(targetInsightId, relation.getTargetEntityId());
        assertEquals(KnowledgeRelationType.DERIVED_FROM, relation.getRelationType());
    }

    @Test
    void shouldMarkTargetSupersededAndCreateRelationForAcceptedSupersession() {
        UUID targetInsightId = java.util.UUID.randomUUID();
        Project project = Project.builder().id(java.util.UUID.randomUUID()).build();
        Analysis analysis = new Analysis();
        Validation validation = new Validation();
        ValidatableProposal proposal = ValidatableProposal.builder()
                .project(project)
                .analysis(analysis)
                .type(ProposalType.INSIGHT)
                .payload(Map.of(
                        "insightType", "TECHNOLOGY_DESCRIPTION",
                        "title", "Angular is now the UI technology",
                        "summary", "The frontend has migrated from Thymeleaf to Angular.",
                        "rationale", "New repository evidence shows the template engine was replaced.",
                        "deltaType", "SUPERSEDES",
                        "targetInsightId", targetInsightId.toString()
                ))
                .build();
        Insight savedInsight = Insight.builder().id(java.util.UUID.randomUUID()).project(project).build();
        Insight targetInsight = Insight.builder().id(targetInsightId).project(project)
                .trustState(InsightTrustState.ACTIVE).build();
        when(repository.save(any(Insight.class))).thenReturn(savedInsight);
        when(repository.findById(targetInsightId)).thenReturn(java.util.Optional.of(targetInsight));

        new InsightPromotionService(repository, relations).promote(proposal, validation, InsightSeverity.INFO);

        assertEquals(InsightTrustState.SUPERSEDED, targetInsight.getTrustState());
        verify(repository, times(2)).save(any(Insight.class));
        ArgumentCaptor<KnowledgeRelation> relationCaptor = ArgumentCaptor.forClass(KnowledgeRelation.class);
        verify(relations).save(relationCaptor.capture());
        KnowledgeRelation relation = relationCaptor.getValue();
        assertEquals(savedInsight.getId(), relation.getSourceEntityId());
        assertEquals(targetInsightId, relation.getTargetEntityId());
        assertEquals(KnowledgeRelationType.SUPERSEDES, relation.getRelationType());
    }

    @Test
    void shouldRejectSupersessionOfNonActiveTarget() {
        UUID targetInsightId = java.util.UUID.randomUUID();
        Project project = Project.builder().id(java.util.UUID.randomUUID()).build();
        Analysis analysis = new Analysis();
        Validation validation = new Validation();
        ValidatableProposal proposal = ValidatableProposal.builder()
                .project(project)
                .analysis(analysis)
                .type(ProposalType.INSIGHT)
                .payload(Map.of(
                        "insightType", "TECHNOLOGY_DESCRIPTION",
                        "title", "Successor",
                        "summary", "Replaces a previously superseded statement.",
                        "rationale", "Evidence.",
                        "deltaType", "SUPERSEDES",
                        "targetInsightId", targetInsightId.toString()
                ))
                .build();
        Insight targetInsight = Insight.builder().id(targetInsightId).project(project)
                .trustState(InsightTrustState.SUPERSEDED).build();
        when(repository.findById(targetInsightId)).thenReturn(java.util.Optional.of(targetInsight));

        InsightPromotionService service = new InsightPromotionService(repository, relations);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.promote(proposal, validation, InsightSeverity.INFO));
        assertEquals("Accepted insight supersession target is not active: " + targetInsightId,
                error.getMessage());
        verify(relations, never()).save(any());
    }
}
