export interface StorySummary {
  readonly id: string;
  readonly number: number;
  readonly title: string;
  readonly status: 'REGISTERED' | 'IN_PROGRESS' | 'COMPLETED';
}

export interface ChallengeSummary {
  readonly id: string;
  readonly title: string;
  readonly status: 'OPEN' | 'RESOLVED' | 'ACCEPTED' | 'MITIGATED';
  readonly impact: string | null;
}

export interface ProposalSummary {
  readonly id: string;
  readonly type: string;
  readonly status: 'PROPOSED' | 'ACCEPTED' | 'REJECTED';
  readonly confidence: number | null;
}

export interface MilestoneSummary {
  readonly id: string;
  readonly name: string;
  readonly status: 'PLANNED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';
}

export interface DecisionSummary {
  readonly id: string;
  readonly title: string;
  readonly choice: string;
  readonly createdAt: string | null;
}

export interface CommitSummary {
  readonly id: string;
  readonly commitHash: string;
  readonly subject: string;
  readonly committedAt: string | null;
  readonly filesChanged: number;
}

export interface ObjectiveSection {
  readonly description: string | null;
  readonly currentMilestone: MilestoneSummary | null;
  readonly activeStory: StorySummary | null;
  readonly openChallenges: readonly ChallengeSummary[];
}

export interface ActiveWorkSection {
  readonly inProgressStories: readonly StorySummary[];
  readonly openChallenges: readonly ChallengeSummary[];
  readonly proposedProposals: readonly ProposalSummary[];
}

export interface RecentChangesSection {
  readonly completedStories: readonly StorySummary[];
  readonly recentDecisions: readonly DecisionSummary[];
  readonly recentCommits: readonly CommitSummary[];
}

export interface RoadmapProgressSection {
  readonly plannedMilestones: readonly MilestoneSummary[];
  readonly registeredStories: readonly StorySummary[];
}

export interface PendingActionsSection {
  readonly proposedProposals: readonly ProposalSummary[];
  readonly openChallenges: readonly ChallengeSummary[];
  readonly unstartedStories: readonly StorySummary[];
}

export interface ProjectState {
  readonly projectId: string;
  readonly projectName: string;
  readonly objective: ObjectiveSection;
  readonly activeWork: ActiveWorkSection;
  readonly recentChanges: RecentChangesSection;
  readonly roadmapProgress: RoadmapProgressSection;
  readonly pendingActions: PendingActionsSection;
}
