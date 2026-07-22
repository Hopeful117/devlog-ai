import { AsyncPipe, DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, map, Observable, of, startWith, switchMap } from 'rxjs';

import { RequestError, toRequestError } from '../../core/http/request-error';
import { ProjectDetail } from './project.models';
import { ProjectSourcesSection } from './project-sources-section';
import { ProjectAnalysesSection } from '../analyses/project-analyses-section';
import { ProjectService } from './project.service';
import { ProjectDeliverablesSection } from '../deliverables/project-deliverables-section';

type ProjectDetailViewState =
  | { readonly state: 'loading' }
  | { readonly state: 'loaded'; readonly project: ProjectDetail }
  | { readonly state: 'not-found' }
  | { readonly state: 'error'; readonly error: RequestError };

@Component({
  selector: 'app-project-detail-page',
  imports: [
    AsyncPipe,
    DatePipe,
    RouterLink,
    ProjectSourcesSection,
    ProjectAnalysesSection,
    ProjectDeliverablesSection,
  ],
  templateUrl: './project-detail-page.html',
  styleUrl: './project-detail-page.scss',
})
export class ProjectDetailPage {
  private readonly route = inject(ActivatedRoute);
  private readonly projectService = inject(ProjectService);

  readonly viewModel$: Observable<ProjectDetailViewState> = this.route.paramMap.pipe(
    map((params) => params.get('id') ?? ''),
    switchMap((identifier) =>
      this.projectService.getProject(identifier).pipe(
        map((project) => ({ state: 'loaded' as const, project })),
        catchError((error: unknown) => {
          const requestError = toRequestError(error);
          return requestError.kind === 'not-found'
            ? of({ state: 'not-found' as const })
            : of({ state: 'error' as const, error: requestError });
        }),
        startWith({ state: 'loading' as const }),
      ),
    ),
  );
}
