package com.hopeful117.devlogai.projectcontextinput.mapper;

import com.hopeful117.devlogai.projectcontextinput.dto.response.ProjectHumanContextInputResponse;
import com.hopeful117.devlogai.projectcontextinput.entity.ProjectHumanContextInput;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProjectHumanContextInputMapper {

    @Mapping(target = "projectId", source = "project.id")
    ProjectHumanContextInputResponse toResponse(ProjectHumanContextInput input);

    List<ProjectHumanContextInputResponse> toResponse(List<ProjectHumanContextInput> inputs);
}
