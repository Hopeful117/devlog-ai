export type ProjectHumanContextInputType =
  'GOAL' | 'CONSTRAINT' | 'ASSUMPTION' | 'KNOWN_GAP' | 'DOMAIN_CONTEXT';

export type ProjectHumanContextInputStatus = 'ACTIVE' | 'ARCHIVED';

export interface ProjectHumanContextInput {
  readonly id: string;
  readonly projectId: string;
  readonly title: string;
  readonly contentMarkdown: string;
  readonly type: ProjectHumanContextInputType;
  readonly status: ProjectHumanContextInputStatus;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface CreateProjectHumanContextInputRequest {
  readonly title: string;
  readonly contentMarkdown: string;
  readonly type: ProjectHumanContextInputType;
}
