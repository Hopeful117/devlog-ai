import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () =>
      import('./features/dashboard/dashboard-page').then((module) => module.DashboardPage),
  },
  {
    path: 'projects',
    loadComponent: () =>
      import('./features/projects/projects-page').then((module) => module.ProjectsPage),
  },
  {
    // The backend identifies projects by slug. The public route keeps the
    // requested :id shape, and the value passed in this segment is the slug.
    path: 'projects/:id',
    loadComponent: () =>
      import('./features/projects/project-detail-page').then((module) => module.ProjectDetailPage),
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
