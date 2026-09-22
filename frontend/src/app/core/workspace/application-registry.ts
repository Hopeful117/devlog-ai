import { environment } from '../../../environments/environment';

export interface ApplicationDefinition {
  readonly id: 'ORGANIZER' | 'DEVLOG';
  readonly label: string;
  readonly destination: string;
}

export const workspaceApplications: readonly ApplicationDefinition[] = [
  { id: 'ORGANIZER', label: 'Organizer', destination: environment.organizerBaseUrl },
  { id: 'DEVLOG', label: 'DevLog', destination: '/projects' },
];
