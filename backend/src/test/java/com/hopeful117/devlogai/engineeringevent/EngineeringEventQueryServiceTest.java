package com.hopeful117.devlogai.engineeringevent;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.proposal.entity.ValidatableProposal;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.validation.entity.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.*;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.shared.exception.InvalidParameterException;

@ExtendWith(MockitoExtension.class)
class EngineeringEventQueryServiceTest {
    @Mock EngineeringEventRepository events;
    @Mock AnalysisEvolutionScopeRepository scopes;
    @Mock ProjectRepository projects;
    @InjectMocks EngineeringEventQueryService service;

    @Test
    void returnsStableImmutableEventProvenance() {
        Project project = Project.builder().id(UUID.randomUUID()).build();
        Analysis analysis = Analysis.builder().id(UUID.randomUUID()).project(project).build();
        Source source = Source.builder().id(UUID.randomUUID()).project(project).build();
        ValidatableProposal proposal = ValidatableProposal.builder().id(UUID.randomUUID())
                .confidence(new BigDecimal("0.8000"))
                .supportingFactIds(List.of(UUID.randomUUID()))
                .supportingObservationIds(List.of(UUID.randomUUID()))
                .evidenceReferences(List.of("git:source:target:file")).build();
        Validation validation = Validation.builder().id(UUID.randomUUID()).build();
        UUID id = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-08-09T10:00:00Z");
        EngineeringEvent event = EngineeringEvent.builder().id(id).project(project).analysis(analysis)
                .source(source).proposal(proposal).validation(validation)
                .category(EngineeringEventCategory.ENGINEERING_IMPROVEMENT).title("Freshness")
                .summary("Explicit freshness was added.").significance("The workflow is deterministic.")
                .baseCommit("a".repeat(40)).targetCommit("b".repeat(40))
                .occurredAt(occurredAt).createdAt(occurredAt).build();
        when(events.findDetailedById(id)).thenReturn(Optional.of(event));
        when(scopes.findById(analysis.getId())).thenReturn(Optional.of(AnalysisEvolutionScope.builder()
                .analysisId(analysis.getId()).comparisonPolicy(EvolutionComparisonPolicy.FIRST_PARENT)
                .mergeCommit(false).build()));

        EngineeringEventResponse response = service.get(id);

        assertAll(
                () -> assertEquals(EngineeringEventResponse.PROJECTION_VERSION, response.version()),
                () -> assertEquals(project.getId(), response.projectId()),
                () -> assertEquals("a".repeat(40), response.baseCommit()),
                () -> assertEquals("b".repeat(40), response.targetCommit()),
                () -> assertEquals(EvolutionComparisonPolicy.FIRST_PARENT, response.comparisonPolicy()),
                () -> assertEquals(List.of("git:source:target:file"), response.evidenceReferences()));
    }

    @Test
    void returnsBoundedPagedProjectEvents() {
        Project project = Project.builder().id(UUID.randomUUID()).build();
        Analysis analysis = Analysis.builder().id(UUID.randomUUID()).project(project).build();
        Source source = Source.builder().id(UUID.randomUUID()).project(project).build();
        EngineeringEvent event = EngineeringEvent.builder().id(UUID.randomUUID()).project(project)
                .analysis(analysis).source(source)
                .proposal(ValidatableProposal.builder().id(UUID.randomUUID()).build())
                .validation(Validation.builder().id(UUID.randomUUID()).build())
                .category(EngineeringEventCategory.FEATURE_INTRODUCTION).title("Event")
                .summary("Summary").significance("Significance")
                .baseCommit("a".repeat(40)).targetCommit("b".repeat(40))
                .occurredAt(Instant.EPOCH).createdAt(Instant.EPOCH).build();
        AnalysisEvolutionScope scope = AnalysisEvolutionScope.builder().analysisId(analysis.getId())
                .comparisonPolicy(EvolutionComparisonPolicy.FIRST_PARENT).build();
        assertTrue(scope.isNew());
        assertEquals(analysis.getId(), scope.getId());
        when(projects.existsById(project.getId())).thenReturn(true);
        when(events.findByProjectIdOrderByOccurredAtDescTargetCommitDescIdAsc(
                eq(project.getId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(event)));
        when(scopes.findAllById(any())).thenReturn(List.of(scope));

        EngineeringEventPageResponse response = service.byProject(project.getId(), 0, 500);

        assertEquals(50, response.size());
        assertEquals(1, response.totalElements());
        assertEquals(event.getId(), response.items().getFirst().id());
    }

    @Test
    void rejectsUnknownProjectsNegativePagesAndUnknownEvents() {
        UUID projectId = UUID.randomUUID();
        when(projects.existsById(projectId)).thenReturn(false, true);
        assertThrows(EntityNotFoundException.class, () -> service.byProject(projectId, 0, 20));
        assertThrows(InvalidParameterException.class, () -> service.byProject(projectId, -1, 20));
        UUID eventId = UUID.randomUUID();
        when(events.findDetailedById(eventId)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> service.get(eventId));
    }
}
