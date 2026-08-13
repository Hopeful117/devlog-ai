package com.hopeful117.devlogai.projectstate.dto.response;

import com.hopeful117.devlogai.projectstate.dto.inner.ChallengeSummary;
import com.hopeful117.devlogai.projectstate.dto.inner.HumanContextInputSummary;
import com.hopeful117.devlogai.projectstate.dto.inner.MilestoneSummary;
import com.hopeful117.devlogai.projectstate.dto.inner.StorySummary;

import java.util.List;

public record ObjectiveSection(
        String description,
        MilestoneSummary currentMilestone,
        StorySummary activeStory,
        List<ChallengeSummary> openChallenges,
        List<HumanContextInputSummary> humanContextInputs
) {
}
