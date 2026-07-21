package com.hopeful117.devlogai.milestone.mapper;

import com.hopeful117.devlogai.milestone.dto.request.CreateMilestoneRequest;
import com.hopeful117.devlogai.milestone.dto.response.MilestoneResponse;
import com.hopeful117.devlogai.milestone.entity.Milestone;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MilestoneMapper {
    @Mapping(
            target = "projectId",
            source = "project.id"
    )
    MilestoneResponse toResponse(
            Milestone milestone
    );

    Milestone toEntity(
            CreateMilestoneRequest request
    );
}
