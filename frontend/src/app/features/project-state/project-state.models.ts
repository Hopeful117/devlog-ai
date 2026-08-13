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

export interface HumanContextInputSummary {
  readonly id: string;
  readonly type: 'GOAL' | 'CONSTRAINT' | 'ASSUMPTION' | 'KNOWN_GAP' | 'DOMAIN_CONTEXT';
  readonly title: string;
  readonly contentMarkdown: string;
  readonly status: 'ACTIVE' | 'ARCHIVED';
  readonly updatedAt: string | null;
}

export interface ProposalSummary {
  readonly id: string;
  readonly type: string;
  readonly insightType: string | null;
  readonly title: string | null;
  readonly summary: string | null;
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

export type KnowledgeEventType =
  | 'FEATURE'
  | 'BUG'
  | 'REFACTORING'
  | 'ARCHITECTURE'
  | 'DOCUMENTATION'
  | 'DEPENDENCY'
  | 'SECURITY'
  | 'PERFORMANCE'
  | 'TEST'
  | 'DEPLOYMENT'
  | 'OTHER';

export type EngineeringEventCategory =
  | 'FEATURE_INTRODUCTION'
  | 'BUG_RESOLUTION'
  | 'ARCHITECTURE_CHANGE'
  | 'TECHNOLOGY_CHANGE'
  | 'ENGINEERING_IMPROVEMENT'
  | 'INFRASTRUCTURE_CHANGE';

export interface KnowledgeSummary {
  readonly id: string;
  readonly type: KnowledgeEventType;
  readonly title: string;
  readonly createdAt: string | null;
}

export interface EvolutionSummary {
  readonly id: string;
  readonly category: EngineeringEventCategory;
  readonly title: string;
  readonly baseCommit: string;
  readonly targetCommit: string;
  readonly occurredAt: string;
}

export interface RecentKnowledgeSection {
  readonly recentKnowledge: readonly KnowledgeSummary[];
}

export interface RecentEvolutionSection {
  readonly recentEvolution: readonly EvolutionSummary[];
}

export interface ObjectiveSection {
  readonly description: string | null;
  readonly currentMilestone: MilestoneSummary | null;
  readonly activeStory: StorySummary | null;
  readonly openChallenges: readonly ChallengeSummary[];
  readonly humanContextInputs: readonly HumanContextInputSummary[];
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
  readonly recentKnowledge: RecentKnowledgeSection;
  readonly recentEvolution: RecentEvolutionSection;
}
