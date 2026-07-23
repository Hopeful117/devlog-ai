export type ProjectStatus = 'ACTIVE' | 'ARCHIVED' | 'PAUSED';

interface ProjectFields {
  readonly id: string;
  readonly name: string;
  readonly slug: string;
  readonly description: string | null;
  readonly status: ProjectStatus;
  readonly createdAt: string;
  readonly updatedAt: string;
}

// The backend currently returns ProjectResponse for both endpoints. Keeping
// semantic types here lets either contract evolve without leaking HTTP DTOs
// into components as untyped values.
export interface ProjectSummary extends ProjectFields {}

export interface ProjectDetail extends ProjectFields {}

export interface CreateProjectRequest {
  readonly name: string;
  readonly description?: string;
}
