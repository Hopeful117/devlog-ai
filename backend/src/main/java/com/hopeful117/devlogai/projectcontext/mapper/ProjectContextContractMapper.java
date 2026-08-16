package com.hopeful117.devlogai.projectcontext.mapper;

import com.hopeful117.devlogai.contracts.projectcontext.ProjectContext;
import com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot;
import org.springframework.stereotype.Component;

@Component
public class ProjectContextContractMapper {

    public ProjectContext toContract(ProjectContextSnapshot snapshot) {
        var project = snapshot.project();

        return new ProjectContext(
                project.id(),
                project.name(),
                project.slug(),
                project.description(),
                project.status().name()
        );
    }

}
