package com.hopeful117.devlogai.projectstate.dto;

import com.hopeful117.devlogai.projectstate.dto.response.ActiveWorkSection;
import com.hopeful117.devlogai.projectstate.dto.response.ObjectiveSection;
import com.hopeful117.devlogai.projectstate.dto.response.PendingActionsSection;
import com.hopeful117.devlogai.projectstate.dto.response.RecentChangesSection;
import com.hopeful117.devlogai.projectstate.dto.response.RecentEvolutionSection;
import com.hopeful117.devlogai.projectstate.dto.response.RecentKnowledgeSection;
import com.hopeful117.devlogai.projectstate.dto.response.RoadmapProgressSection;

public record ProjectStateSections(
        ObjectiveSection objective,
        ActiveWorkSection activeWork,
        RecentChangesSection recentChanges,
        RoadmapProgressSection roadmapProgress,
        PendingActionsSection pendingActions,
        RecentKnowledgeSection recentKnowledge,
        RecentEvolutionSection recentEvolution
) {
}