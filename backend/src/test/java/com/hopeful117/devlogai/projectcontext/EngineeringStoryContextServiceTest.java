package com.hopeful117.devlogai.projectcontext;

import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EngineeringStoryContextServiceTest {

    @Mock
    ProjectContextProvider projectContextProvider;

    @Mock
    RepositoryContextAdapter repositoryContextAdapter;

    @InjectMocks
    EngineeringStoryContextServiceImpl service;

    @Test
    void shouldBuildEngineeringStoryContext() {
        UUID projectId = UUID.randomUUID();
        ProjectContextSnapshot snapshot = mock(ProjectContextSnapshot.class);

        when(projectContextProvider.build(projectId)).thenReturn(snapshot);

        EngineeringStoryContext context = service.build(projectId);

        assertNotNull(context);
        assertEquals(projectId, context.projectId());
        assertEquals(snapshot, context.projectContext());
        assertNotNull(context.generatedAt());
        verify(projectContextProvider).build(projectId);
    }

    @Test
    void shouldPropagateExceptionWhenProjectNotFound() {
        UUID projectId = UUID.randomUUID();

        when(projectContextProvider.build(projectId))
                .thenThrow(new EntityNotFoundException("Project", projectId));

        assertThrows(EntityNotFoundException.class, () -> service.build(projectId));
        verify(projectContextProvider).build(projectId);
    }

    @Test
    void shouldBuildWithRepositoryContext() {
        UUID projectId = UUID.randomUUID();
        String description = "Add authentication module";
        ProjectContextSnapshot snapshot = mock(ProjectContextSnapshot.class);
        RepositoryContext repositoryContext = mock(RepositoryContext.class);

        when(projectContextProvider.build(projectId)).thenReturn(snapshot);
        when(repositoryContextAdapter.buildRepositoryContext(projectId, description))
                .thenReturn(repositoryContext);

        EngineeringStoryContext context =
                service.buildWithRepositoryContext(projectId, description);

        assertNotNull(context);
        assertEquals(projectId, context.projectId());
        assertEquals(snapshot, context.projectContext());
        assertEquals(repositoryContext, context.repositoryContext());
        assertNotNull(context.generatedAt());
        verify(projectContextProvider).build(projectId);
        verify(repositoryContextAdapter)
                .buildRepositoryContext(projectId, description);
    }
}