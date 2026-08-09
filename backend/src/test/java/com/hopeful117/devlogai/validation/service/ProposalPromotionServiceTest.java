package com.hopeful117.devlogai.validation.service;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.engineeringevent.*;
import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import com.hopeful117.devlogai.insight.service.InsightPromotionService;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.proposal.entity.*;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.validation.entity.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProposalPromotionServiceTest {
    @Mock InsightPromotionService insights;
    @Mock EngineeringEventRepository events;
    @Mock AnalysisEvolutionScopeRepository scopes;
    @InjectMocks ProposalPromotionService service;

    @Test
    void promotesAnEngineeringEventWithImmutableEvolutionProvenance() {
        UUID projectId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).build();
        Analysis analysis = Analysis.builder().id(analysisId).project(project).build();
        Source source = Source.builder().id(UUID.randomUUID()).project(project).build();
        ValidatableProposal proposal = ValidatableProposal.builder().id(UUID.randomUUID())
                .project(project).analysis(analysis).type(ProposalType.ENGINEERING_EVENT)
                .payload(Map.of("category", "ARCHITECTURE_CHANGE", "title", "Split core",
                        "summary", "The core was split.", "significance", "Boundaries are explicit."))
                .build();
        Validation validation = Validation.builder().id(UUID.randomUUID()).proposal(proposal).build();
        Instant occurredAt = Instant.parse("2026-08-09T10:00:00Z");
        when(scopes.findById(analysisId)).thenReturn(Optional.of(AnalysisEvolutionScope.builder()
                .analysisId(analysisId).analysis(analysis).project(project).source(source)
                .baseCommit("a".repeat(40)).targetCommit("b".repeat(40))
                .targetCommittedAt(occurredAt).build()));

        service.promote(proposal, validation, null);

        ArgumentCaptor<EngineeringEvent> saved = ArgumentCaptor.forClass(EngineeringEvent.class);
        verify(events).save(saved.capture());
        assertAll(
                () -> assertEquals(projectId, saved.getValue().getProject().getId()),
                () -> assertEquals("a".repeat(40), saved.getValue().getBaseCommit()),
                () -> assertEquals("b".repeat(40), saved.getValue().getTargetCommit()),
                () -> assertEquals(occurredAt, saved.getValue().getOccurredAt()),
                () -> assertEquals(EngineeringEventCategory.ARCHITECTURE_CHANGE,
                        saved.getValue().getCategory()));
    }

    @Test
    void retainsInsightPromotionAndRejectsUnsupportedAcceptedTypes() {
        ValidatableProposal insight = ValidatableProposal.builder().type(ProposalType.INSIGHT).build();
        Validation validation = Validation.builder().build();
        service.promote(insight, validation, InsightSeverity.WARNING);
        verify(insights).promote(insight, validation, InsightSeverity.WARNING);

        ValidatableProposal decision = ValidatableProposal.builder()
                .type(ProposalType.ENGINEERING_DECISION).build();
        assertThrows(IllegalArgumentException.class,
                () -> service.promote(decision, validation, null));
    }

    @Test
    void rejectsInsightSeverityForEngineeringEvents() {
        ValidatableProposal event = ValidatableProposal.builder()
                .type(ProposalType.ENGINEERING_EVENT).build();
        Validation validation = Validation.builder().build();
        assertThrows(IllegalArgumentException.class,
                () -> service.promote(event, validation, InsightSeverity.WARNING));
        verifyNoInteractions(events, scopes);
    }
}
