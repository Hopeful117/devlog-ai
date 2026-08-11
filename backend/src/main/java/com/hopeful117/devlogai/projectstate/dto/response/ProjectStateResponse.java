package com.hopeful117.devlogai.projectstate.dto.response;

import java.util.UUID;

public record ProjectStateResponse(
        UUID projectId,
        String projectName,
        ObjectiveSection objective,
        ActiveWorkSection activeWork,
        RecentChangesSection recentChanges,
        RoadmapProgressSection roadmapProgress,
        PendingActionsSection pendingActions,
        RecentKnowledgeSection recentKnowledge,
        RecentEvolutionSection recentEvolution
) {
}
