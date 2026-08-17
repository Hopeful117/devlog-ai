package com.hopeful117.devlogai.engineeringcontext;

import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContext;
import com.hopeful117.devlogai.engineeringcontext.mapper.EngineeringContextContractMapper;
import com.hopeful117.devlogai.project.service.ProjectService;
import com.hopeful117.devlogai.projectcontext.ProjectContextProvider;
import com.hopeful117.devlogai.projectcontext.RepositoryContextAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EngineeringContextFacadeImpl implements EngineeringContextFacade {
    private final ProjectService projectService;
    private final ProjectContextProvider projectContextProvider;
    private final RepositoryContextAdapter repositoryContextAdapter;
    private final EngineeringContextContractMapper mapper;

    @Override
    public EngineeringContext getEngineeringContext(String projectSlug, String intent) {
        var project = projectService.getBySlug(projectSlug);
        var projectId = project.getId();

        var projectContext =
                projectContextProvider.build(projectId);

        var repositoryContext =
                repositoryContextAdapter.buildRepositoryContext(
                        projectId,
                        intent,
                        projectContext
                );

        return mapper.toContract(
                projectContext,
                repositoryContext,
                intent
        );
    }
}
