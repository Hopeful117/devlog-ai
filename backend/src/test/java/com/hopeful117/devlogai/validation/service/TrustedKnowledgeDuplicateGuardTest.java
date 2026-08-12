package com.hopeful117.devlogai.validation.service;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import com.hopeful117.devlogai.insight.entity.InsightType;
import com.hopeful117.devlogai.insight.repository.InsightRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.proposal.entity.ProposalType;
import com.hopeful117.devlogai.proposal.entity.ValidatableProposal;
import com.hopeful117.devlogai.shared.exception.ConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrustedKnowledgeDuplicateGuardTest {
    @Mock
    InsightRepository insightRepository;

    @Test
    void shouldRejectExactDuplicateNewInsight() {
        UUID projectId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).build();
        ValidatableProposal proposal = insightProposal(project, Map.of(
                "insightType", "ARCHITECTURE_DESCRIPTION",
                "title", "Modular architecture",
                "summary", "The application is split into bounded modules.",
                "rationale", "Boundaries keep modules independently deployable."
        ));
        when(insightRepository.findByProjectIdOrderByCreatedAtDescIdDesc(projectId)).thenReturn(List.of(
                trustedInsight(project, InsightType.ARCHITECTURAL, "ARCHITECTURE_DESCRIPTION",
                        " modular architecture ",
                        "The application is split   into bounded modules.",
                        "boundaries keep modules independently deployable.")
        ));

        TrustedKnowledgeDuplicateGuard guard = new TrustedKnowledgeDuplicateGuard(insightRepository);

        assertThrows(ConflictException.class, () -> guard.assertCanAccept(proposal));
    }

    @Test
    void shouldAllowNonDuplicateNewInsight() {
        UUID projectId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).build();
        ValidatableProposal proposal = insightProposal(project, Map.of(
                "insightType", "TECHNOLOGY_DESCRIPTION",
                "title", "Containerized delivery",
                "summary", "The project uses containers for delivery.",
                "rationale", "Build evidence shows image packaging."
        ));
        when(insightRepository.findByProjectIdOrderByCreatedAtDescIdDesc(projectId)).thenReturn(List.of(
                trustedInsight(project, InsightType.ARCHITECTURAL, "ARCHITECTURE_DESCRIPTION",
                        "Modular architecture",
                        "The application is split into bounded modules.",
                        "Boundaries keep modules independently deployable.")
        ));

        TrustedKnowledgeDuplicateGuard guard = new TrustedKnowledgeDuplicateGuard(insightRepository);

        assertDoesNotThrow(() -> guard.assertCanAccept(proposal));
    }

    @Test
    void shouldAllowLegitimateEnrichment() {
        UUID projectId = UUID.randomUUID();
        UUID targetInsightId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).build();
        ValidatableProposal proposal = insightProposal(project, Map.of(
                "insightType", "ARCHITECTURE_DESCRIPTION",
                "title", "Modular architecture",
                "summary", "The application is split into bounded modules and deployment cadence is isolated.",
                "rationale", "New evidence shows modules ship independently.",
                "deltaType", "ENRICHES",
                "targetInsightId", targetInsightId.toString()
        ));
        when(insightRepository.findByProjectIdOrderByCreatedAtDescIdDesc(projectId)).thenReturn(List.of(
                trustedInsight(project, InsightType.ARCHITECTURAL, "ARCHITECTURE_DESCRIPTION",
                        "Modular architecture",
                        "The application is split into bounded modules.",
                        "Boundaries keep modules independently deployable.")
        ));

        TrustedKnowledgeDuplicateGuard guard = new TrustedKnowledgeDuplicateGuard(insightRepository);

        assertDoesNotThrow(() -> guard.assertCanAccept(proposal));
    }

    @Test
    void shouldRejectRestatementEnrichment() {
        UUID projectId = UUID.randomUUID();
        UUID targetInsightId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).build();
        ValidatableProposal proposal = insightProposal(project, Map.of(
                "insightType", "ARCHITECTURE_DESCRIPTION",
                "title", "Modular architecture",
                "summary", "The application is split into bounded modules.",
                "rationale", "Boundaries keep modules independently deployable.",
                "deltaType", "ENRICHES",
                "targetInsightId", targetInsightId.toString()
        ));
        when(insightRepository.findByProjectIdOrderByCreatedAtDescIdDesc(projectId)).thenReturn(List.of(
                trustedInsight(project, InsightType.ARCHITECTURAL, "ARCHITECTURE_DESCRIPTION",
                        "Modular architecture",
                        "The application is split into bounded modules.",
                        "Boundaries keep modules independently deployable.")
        ));

        TrustedKnowledgeDuplicateGuard guard = new TrustedKnowledgeDuplicateGuard(insightRepository);

        assertThrows(ConflictException.class, () -> guard.assertCanAccept(proposal));
    }

    @Test
    void shouldIgnoreNonInsightProposal() {
        UUID projectId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).build();
        ValidatableProposal proposal = ValidatableProposal.builder()
                .project(project)
                .analysis(new Analysis())
                .type(ProposalType.ENGINEERING_EVENT)
                .build();

        TrustedKnowledgeDuplicateGuard guard = new TrustedKnowledgeDuplicateGuard(insightRepository);

        assertDoesNotThrow(() -> guard.assertCanAccept(proposal));
    }

    @Test
    void shouldQueryTrustedKnowledgeOnlyWithinProposalProject() {
        UUID projectId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).build();
        ValidatableProposal proposal = insightProposal(project, Map.of(
                "insightType", "TECHNOLOGY_DESCRIPTION",
                "title", "Containerized delivery",
                "summary", "The project uses containers for delivery.",
                "rationale", "Build evidence shows image packaging."
        ));
        when(insightRepository.findByProjectIdOrderByCreatedAtDescIdDesc(projectId)).thenReturn(List.of());

        TrustedKnowledgeDuplicateGuard guard = new TrustedKnowledgeDuplicateGuard(insightRepository);

        assertDoesNotThrow(() -> guard.assertCanAccept(proposal));
        verify(insightRepository).findByProjectIdOrderByCreatedAtDescIdDesc(projectId);
    }

    private ValidatableProposal insightProposal(Project project, Map<String, Object> payload) {
        return ValidatableProposal.builder()
                .project(project)
                .analysis(new Analysis())
                .type(ProposalType.INSIGHT)
                .payload(payload)
                .build();
    }

    private Insight trustedInsight(
            Project project,
            InsightType type,
            String sourceType,
            String title,
            String content,
            String rationale
    ) {
        return Insight.builder()
                .id(UUID.randomUUID())
                .project(project)
                .analysis(new Analysis())
                .type(type)
                .severity(InsightSeverity.INFO)
                .title(title)
                .content(content)
                .rationale(rationale)
                .sourceType(sourceType)
                .build();
    }
}
