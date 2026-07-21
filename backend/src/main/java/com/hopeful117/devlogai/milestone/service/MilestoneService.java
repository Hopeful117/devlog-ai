package com.hopeful117.devlogai.milestone.service;

import com.hopeful117.devlogai.milestone.dto.request.CreateMilestoneRequest;
import com.hopeful117.devlogai.milestone.dto.response.MilestoneResponse;
import com.hopeful117.devlogai.milestone.entity.MilestoneStatus;

import java.util.List;
import java.util.UUID;

public interface MilestoneService {
    MilestoneResponse create(CreateMilestoneRequest request);

    MilestoneResponse getById(UUID id);

    List<MilestoneResponse> getByProject(UUID projectId);

    List<MilestoneResponse> getByProjectAndStatus(
            UUID projectId,
            MilestoneStatus status
    );
}
