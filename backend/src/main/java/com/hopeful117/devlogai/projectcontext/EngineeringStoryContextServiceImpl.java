package com.hopeful117.devlogai.projectcontext;

import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EngineeringStoryContextServiceImpl implements EngineeringStoryContextService {

    private final ProjectContextProvider projectContextProvider;
    private final RepositoryContextAdapter repositoryContextAdapter;

    @Override
    public EngineeringStoryContext build(UUID projectId) {
        ProjectContextSnapshot snapshot = projectContextProvider.build(projectId);
        return new EngineeringStoryContext(snapshot, Instant.now(), projectId, null);
    }

    @Override
    public EngineeringStoryContext buildWithRepositoryContext(
            UUID projectId, String storyDescription) {
        ProjectContextSnapshot snapshot = projectContextProvider.build(projectId);
        RepositoryContext repositoryContext =
                repositoryContextAdapter.buildRepositoryContext(
                        projectId, storyDescription);
        return new EngineeringStoryContext(
                snapshot, Instant.now(), projectId, repositoryContext);
    }
}