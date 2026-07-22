export type SourceType = 'GIT_REPOSITORY';

export type GitProvider = 'GITHUB' | 'GITLAB' | 'BITBUCKET' | 'AZURE_DEVOPS' | 'GENERIC_GIT';

interface SourceFields {
  readonly id: string;
  readonly projectId: string;
  readonly type: SourceType;
  readonly name: string;
  readonly repositoryUrl: string;
  readonly defaultBranch: string | null;
  readonly provider: GitProvider | null;
  readonly active: boolean;
  readonly lastSynchronizedAt: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface SourceSummary extends SourceFields {}

export interface SourceDetail extends SourceFields {}

export interface CreateSourceRequest {
  readonly projectId: string;
  readonly type: SourceType;
  readonly name: string;
  readonly repositoryUrl: string;
  readonly defaultBranch: string | null;
  readonly provider: GitProvider | null;
}

export interface UpdateSourceActivationRequest {
  readonly active: boolean;
}
