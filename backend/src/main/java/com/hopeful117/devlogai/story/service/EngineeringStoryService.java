package com.hopeful117.devlogai.story.service;

import com.hopeful117.devlogai.story.dto.request.CompleteStoryRequest;
import com.hopeful117.devlogai.story.dto.request.CreateEngineeringStoryRequest;
import com.hopeful117.devlogai.story.dto.request.StartStoryRequest;
import com.hopeful117.devlogai.story.dto.response.EngineeringStoryResponse;

import java.util.List;
import java.util.UUID;

public interface EngineeringStoryService {

    EngineeringStoryResponse register(CreateEngineeringStoryRequest request);

    EngineeringStoryResponse startImplementation(UUID storyId, UUID projectId, StartStoryRequest request);

    EngineeringStoryResponse complete(UUID storyId, UUID projectId, CompleteStoryRequest request);

    EngineeringStoryResponse getById(UUID storyId, UUID projectId);

    List<EngineeringStoryResponse> getByProject(UUID projectId);
}