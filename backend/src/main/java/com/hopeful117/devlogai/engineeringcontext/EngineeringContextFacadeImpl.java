package com.hopeful117.devlogai.engineeringcontext;

import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContext;
import com.hopeful117.devlogai.engineeringcontext.mapper.EngineeringContextContractMapper;
import com.hopeful117.devlogai.project.service.ProjectService;
import com.hopeful117.devlogai.projectcontext.ProjectContextProvider;
import com.hopeful117.devlogai.projectcontext.RepositoryContextAdapter;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EngineeringContextFacadeImpl implements EngineeringContextFacade {
    private final ProjectService projectService;
    private final ProjectContextProvider projectContextProvider;
    private final RepositoryContextAdapter repositoryContextAdapter;
    private final ProjectFreshnessService freshnessService;
    private final EngineeringContextContractMapper mapper;

    @Override
    public EngineeringContext getEngineeringContext(
            String projectSlug,
            String intent,
            List<String> files,
            UUID storyId
    ) {
        var project = projectService.getBySlug(projectSlug);
        var projectId = project.getId();

        var projectContext =
                projectContextProvider.build(projectId);

        var repositoryContext =
                repositoryContextAdapter.buildRepositoryContext(
                        projectId,
                        intent,
                        projectContext,
                        files,
                        storyId
                );

        return mapper.toContract(
                projectContext,
                repositoryContext,
                intent,
                files,
                storyId,
                freshnessService.summary(projectId)
        );
    }
}