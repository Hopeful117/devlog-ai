package com.hopeful117.devlogai.insight.service;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import com.hopeful117.devlogai.insight.entity.InsightType;
import com.hopeful117.devlogai.insight.repository.InsightRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.proposal.entity.ProposalType;
import com.hopeful117.devlogai.proposal.entity.ValidatableProposal;
import com.hopeful117.devlogai.validation.entity.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InsightPromotionServiceTest {
    @Mock InsightRepository repository;

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
                        "summary", "The application is split into bounded modules."
                ))
                .build();

        new InsightPromotionService(repository).promote(proposal, validation, InsightSeverity.WARNING);

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
                () -> assertEquals("The application is split into bounded modules.", insight.getContent())
        );
    }

    @Test
    void shouldIgnoreNonInsightProposal() {
        ValidatableProposal proposal = ValidatableProposal.builder()
                .type(ProposalType.DOCUMENTATION)
                .build();
        new InsightPromotionService(repository).promote(proposal, new Validation(), null);
        verifyNoInteractions(repository);
    }

    @Test
    void shouldRejectIncompleteInsightPayload() {
        ValidatableProposal proposal = ValidatableProposal.builder()
                .type(ProposalType.INSIGHT)
                .payload(Map.of("insightType", "TECHNOLOGY_DESCRIPTION", "title", "Stack"))
                .build();
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new InsightPromotionService(repository).promote(
                        proposal, new Validation(), InsightSeverity.INFO));
        assertEquals("Accepted insight proposal is missing payload field: summary", error.getMessage());
        verifyNoInteractions(repository);
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
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new InsightPromotionService(repository).promote(proposal, new Validation(), null));
        assertEquals("Severity is required when accepting an insight proposal", error.getMessage());
        verifyNoInteractions(repository);
    }
}
