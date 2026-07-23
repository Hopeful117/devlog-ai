import { AsyncPipe, DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, map, of, startWith, switchMap } from 'rxjs';
import { ProjectAnalysesSection } from '../analyses/project-analyses-section';
import { ProjectDeliverablesSection } from '../deliverables/project-deliverables-section';
import { InsightService } from '../insights/insight.service';
import { ProjectService } from '../projects/project.service';
import { ProjectSourcesSection } from '../projects/project-sources-section';

type WorkspaceSection = 'activity' | 'knowledge' | 'documentation' | 'settings';

@Component({
  selector: 'app-project-workspace-section-page',
  imports: [
    AsyncPipe,
    DatePipe,
    RouterLink,
    ProjectAnalysesSection,
    ProjectDeliverablesSection,
    ProjectSourcesSection,
  ],
  templateUrl: './project-workspace-section-page.html',
  styleUrl: './project-workspace-section-page.scss',
})
export class ProjectWorkspaceSectionPage {
  private readonly route = inject(ActivatedRoute);
  private readonly projectService = inject(ProjectService);
  private readonly insightService = inject(InsightService);

  readonly section = this.route.snapshot.data['workspaceSection'] as WorkspaceSection;
  readonly project$ = this.route.parent!.paramMap.pipe(
    map((params) => params.get('id') ?? ''),
    switchMap((identifier) =>
      this.projectService.getProject(identifier).pipe(
        map((project) => ({ state: 'loaded' as const, project })),
        catchError(() => of({ state: 'error' as const })),
        startWith({ state: 'loading' as const }),
      ),
    ),
  );

  readonly knowledge$ = this.project$.pipe(
    switchMap((view) =>
      view.state === 'loaded'
        ? this.insightService.getInsightsByProject(view.project.id).pipe(
            map((items) => ({ state: 'loaded' as const, items })),
            catchError(() => of({ state: 'error' as const })),
            startWith({ state: 'loading' as const }),
          )
        : of({ state: 'loading' as const }),
    ),
  );
}
