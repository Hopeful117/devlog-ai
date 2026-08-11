package com.hopeful117.devlogai.projectstate.dto.inner;

import com.hopeful117.devlogai.milestone.entity.MilestoneStatus;

import java.util.UUID;

public record MilestoneSummary(
        UUID id,
        String name,
        MilestoneStatus status
) {
}
