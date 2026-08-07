package com.hopeful117.devlogai.projectcontext;

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
}