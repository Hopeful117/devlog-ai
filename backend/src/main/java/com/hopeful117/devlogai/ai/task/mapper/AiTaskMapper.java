package com.hopeful117.devlogai.ai.task.mapper;

import com.hopeful117.devlogai.ai.task.dto.request.CreateAiTaskRequest;
import com.hopeful117.devlogai.ai.task.dto.response.AiTaskResponse;
import com.hopeful117.devlogai.ai.task.entity.AiTask;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AiTaskMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "analysis", ignore = true)
    @Mapping(target = "correlationId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "contextSnapshot", ignore = true)
    @Mapping(target = "externalJobId", ignore = true)
    @Mapping(target = "attemptCount", ignore = true)
    @Mapping(target = "failureCode", ignore = true)
    @Mapping(target = "failureMessage", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "submittedAt", ignore = true)
    @Mapping(target = "startedAt", ignore = true)
    @Mapping(target = "completedAt", ignore = true)
    AiTask toEntity(CreateAiTaskRequest request);

    @Mapping(target = "analysisId", source = "analysis.id")
    AiTaskResponse toResponse(AiTask entity);
}
