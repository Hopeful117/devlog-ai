package com.hopeful117.devlogai.project.mapper;

import com.hopeful117.devlogai.project.dto.request.CreateProjectRequest;
import com.hopeful117.devlogai.project.dto.request.UpdateProjectRequest;
import com.hopeful117.devlogai.project.dto.response.ProjectResponse;
import com.hopeful117.devlogai.project.entity.Project;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProjectMapper {
    Project toEntity(CreateProjectRequest request);

    ProjectResponse toResponse(Project project);

    List<ProjectResponse> toResponse(List<Project> projects);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateProject(UpdateProjectRequest request,
                       @MappingTarget Project project);

}
