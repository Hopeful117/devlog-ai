package com.hopeful117.devlogai.projectstate.dto.response;

import com.hopeful117.devlogai.projectstate.dto.inner.CommitSummary;
import com.hopeful117.devlogai.projectstate.dto.inner.DecisionSummary;
import com.hopeful117.devlogai.projectstate.dto.inner.StorySummary;

import java.util.List;

public record RecentChangesSection(
        List<StorySummary> completedStories,
        List<DecisionSummary> recentDecisions,
        List<CommitSummary> recentCommits
) {
}
