export interface EngineeringStory {
  readonly id: string;
  readonly projectId: string;
  readonly storyNumber: number | null;
  readonly title: string;
  readonly storyPath: string | null;
  readonly baseCommit: string | null;
  readonly targetCommit: string | null;
  readonly status: string;
  readonly createdAt: string | null;
  readonly updatedAt: string | null;
  readonly completedAt: string | null;
}
export interface StoryChangeReference {
  readonly type: string;
  readonly ref: string;
  readonly scope: string;
}
export interface StoryChange {
  readonly reference: StoryChangeReference;
  readonly kind: string;
  readonly summary: string | null;
  readonly occurredAt: string | null;
  readonly provenance: Readonly<Record<string, string>>;
}
export interface StoryChangeBriefing {
  readonly contractVersion: string;
  readonly storyId: string;
  readonly contextDigest: string;
  readonly projectionDigest: string;
  readonly groundingStatus: 'NOT_ESTABLISHED';
  readonly description: string;
  readonly changes: readonly StoryChange[];
  readonly warnings: readonly string[];
  readonly snapshot: Readonly<Record<string, unknown>>;
}
