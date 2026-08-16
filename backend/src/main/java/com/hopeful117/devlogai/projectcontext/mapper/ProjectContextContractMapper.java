package com.hopeful117.devlogai.projectcontext.mapper;

import com.hopeful117.devlogai.contracts.projectcontext.ProjectContext;
import com.hopeful117.devlogai.contracts.projectcontext.ProjectNote;
import com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot;
import org.springframework.stereotype.Component;

@Component
public class ProjectContextContractMapper {

    public ProjectContext toContract(ProjectContextSnapshot snapshot) {
        var project = snapshot.project();
        var notes = snapshot.humanContextInputs().stream()
                .map(input -> new ProjectNote(
                        input.id(),
                        input.type().name(),
                        input.title(),
                        input.contentMarkdown(),
                        input.status(),
                        input.updatedAt()
                ))
                .toList();

        return new ProjectContext(
                project.id(),
                project.name(),
                project.slug(),
                project.description(),
                project.status().name(),
                notes
        );
    }

}
