import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'projects',
  },
  {
    path: 'projects',
    loadComponent: () =>
      import('./features/projects/projects-page').then((module) => module.ProjectsPage),
  },
  {
    path: 'projects/:id',
    loadComponent: () =>
      import('./features/workspace/project-workspace-layout').then(
        (module) => module.ProjectWorkspaceLayout,
      ),
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./features/projects/project-detail-page').then(
            (module) => module.ProjectDetailPage,
          ),
      },
      {
        path: 'events',
        loadComponent: () =>
          import('./features/engineering-events/engineering-events-page').then(
            (module) => module.EngineeringEventsPage,
          ),
      },
      {
        path: 'activity',
        data: { workspaceSection: 'activity' },
        loadComponent: () =>
          import('./features/workspace/project-workspace-section-page').then(
            (module) => module.ProjectWorkspaceSectionPage,
          ),
      },
      {
        path: 'knowledge',
        data: { workspaceSection: 'knowledge' },
        loadComponent: () =>
          import('./features/workspace/project-workspace-section-page').then(
            (module) => module.ProjectWorkspaceSectionPage,
          ),
      },
      {
        path: 'documentation',
        data: { workspaceSection: 'documentation' },
        loadComponent: () =>
          import('./features/workspace/project-workspace-section-page').then(
            (module) => module.ProjectWorkspaceSectionPage,
          ),
      },
      {
        path: 'settings',
        data: { workspaceSection: 'settings' },
        loadComponent: () =>
          import('./features/workspace/project-workspace-section-page').then(
            (module) => module.ProjectWorkspaceSectionPage,
          ),
      },
    ],
  },
  {
    path: 'engineering-events/:id',
    loadComponent: () =>
      import('./features/engineering-events/engineering-event-detail-page').then(
        (module) => module.EngineeringEventDetailPage,
      ),
  },
  {
    path: 'analyses/:id/proposal-review',
    loadComponent: () =>
      import('./features/insights/proposal-review-page').then(
        (module) => module.ProposalReviewPage,
      ),
  },
  {
    path: 'analyses/:id',
    loadComponent: () =>
      import('./features/analyses/analysis-detail-page').then(
        (module) => module.AnalysisDetailPage,
      ),
  },
  {
    path: 'insights',
    loadComponent: () =>
      import('./features/insights/insights-page').then((module) => module.InsightsPage),
  },
  {
    path: 'proposals/:id',
    loadComponent: () =>
      import('./features/insights/proposal-detail-page').then(
        (module) => module.ProposalDetailPage,
      ),
  },
  {
    path: 'deliverables/:id',
    loadComponent: () =>
      import('./features/deliverables/deliverable-detail-page').then(
        (module) => module.DeliverableDetailPage,
      ),
  },
  {
    path: 'insights/:id',
    loadComponent: () =>
      import('./features/insights/insight-detail-page').then((module) => module.InsightDetailPage),
  },
];
