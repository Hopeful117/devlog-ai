package com.hopeful117.devlogai.milestone.dto.response;

import com.hopeful117.devlogai.milestone.entity.MilestoneStatus;

import java.time.Instant;
import java.util.UUID;

public record MilestoneResponse (

        UUID id,

        UUID projectId,

        String name,

        String description,

        MilestoneStatus status,

        Instant startedAt,

        Instant completedAt,

        Instant createdAt,

        Instant updatedAt


)



{
}
