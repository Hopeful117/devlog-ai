export type TimelineEntryType =
  'STORY_COMPLETED' | 'ENGINEERING_EVENT' | 'KNOWLEDGE_EVENT' | 'DECISION' | 'MILESTONE_COMPLETED';

export interface TimelineEntry {
  readonly id: string;
  readonly type: TimelineEntryType;
  readonly timestamp: string;
  readonly title: string;
  readonly detail: string | null;
}

export interface TimelineResponse {
  readonly projectId: string;
  readonly projectName: string;
  readonly entries: readonly TimelineEntry[];
}
