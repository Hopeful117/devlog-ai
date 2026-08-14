package com.hopeful117.devlogai.contextmaintenance.mapper;

import com.hopeful117.devlogai.contextmaintenance.dto.response.MaintenanceFindingActionResponse;
import com.hopeful117.devlogai.contextmaintenance.dto.response.MaintenanceFindingResponse;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFindingAction;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFinding;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface MaintenanceFindingMapper {

    @Mapping(target = "projectId", source = "project.id")
    @Mapping(target = "actionHistory", source = "actions")
    MaintenanceFindingResponse toResponse(MaintenanceFinding finding);

    MaintenanceFindingActionResponse toResponse(MaintenanceFindingAction action);

    List<MaintenanceFindingResponse> toResponse(List<MaintenanceFinding> findings);
}
