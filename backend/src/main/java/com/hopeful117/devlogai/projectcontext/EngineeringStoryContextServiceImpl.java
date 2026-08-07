package com.hopeful117.devlogai.projectcontext;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EngineeringStoryContextServiceImpl implements EngineeringStoryContextService {

    private final ProjectContextProvider projectContextProvider;

    @Override
    public EngineeringStoryContext build(UUID projectId) {
        ProjectContextSnapshot snapshot = projectContextProvider.build(projectId);
        return new EngineeringStoryContext(snapshot, Instant.now(), projectId);
    }
}