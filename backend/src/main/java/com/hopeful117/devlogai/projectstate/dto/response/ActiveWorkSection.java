package com.hopeful117.devlogai.projectstate.dto.response;

import com.hopeful117.devlogai.projectstate.dto.inner.ChallengeSummary;
import com.hopeful117.devlogai.projectstate.dto.inner.ProposalSummary;
import com.hopeful117.devlogai.projectstate.dto.inner.StorySummary;

import java.util.List;

public record ActiveWorkSection(
        List<StorySummary> inProgressStories,
        List<ChallengeSummary> openChallenges,
        List<ProposalSummary> proposedProposals
) {
}
