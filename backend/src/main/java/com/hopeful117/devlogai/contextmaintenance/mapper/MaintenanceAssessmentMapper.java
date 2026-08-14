package com.hopeful117.devlogai.contextmaintenance.mapper;

import com.hopeful117.devlogai.contextmaintenance.dto.response.MaintenanceAssessmentResponse;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceAssessment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface MaintenanceAssessmentMapper {

    @Mapping(target = "findingId", source = "finding.id")
    MaintenanceAssessmentResponse toResponse(MaintenanceAssessment assessment);

    List<MaintenanceAssessmentResponse> toResponse(List<MaintenanceAssessment> assessments);
}
