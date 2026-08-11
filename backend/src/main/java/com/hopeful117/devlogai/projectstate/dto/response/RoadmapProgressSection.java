package com.hopeful117.devlogai.projectstate.dto.response;

import com.hopeful117.devlogai.projectstate.dto.inner.MilestoneSummary;
import com.hopeful117.devlogai.projectstate.dto.inner.StorySummary;

import java.util.List;

public record RoadmapProgressSection(
        List<MilestoneSummary> plannedMilestones,
        List<StorySummary> registeredStories
) {
}
