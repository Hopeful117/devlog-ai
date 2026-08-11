import { AsyncPipe, DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, forkJoin, map, of, shareReplay, startWith, switchMap } from 'rxjs';

import { RequestError, toRequestError } from '../../core/http/request-error';
import { ProjectService } from '../projects/project.service';
import { ProjectStateService } from './project-state.service';

type OverviewViewState =
  | { readonly state: 'loading' }
  | {
      readonly state: 'loaded';
      readonly project: { readonly id: string; readonly name: string; readonly description: string | null };
      readonly state: import('./project-state.models').ProjectState;
    }
  | { readonly state: 'not-found' }
  | { readonly state: 'error'; readonly error: RequestError };

@Component({
  selector: 'app-project-state-page',
  imports: [AsyncPipe, DatePipe, RouterLink],
  templateUrl: './project-state-page.html',
  styleUrl: './project-state-page.scss',
})
export class ProjectStatePage {
  private readonly route = inject(ActivatedRoute);
  private readonly projectService = inject(ProjectService);
  private readonly stateService = inject(ProjectStateService);

  readonly viewModel$ = this.route.paramMap.pipe(
    map((params) => params.get('id') ?? ''),
    switchMap((identifier) =>
      this.projectService.getProject(identifier).pipe(
        switchMap((project) =>
          this.stateService.getProjectState(project.id).pipe(
            map((state) => ({ state: 'loaded' as const, project, state })),
            catchError((error: unknown) =>
              of<OverviewViewState>({ state: 'error' as const, error: toRequestError(error, 'project-state') }),
            ),
          ),
        ),
        catchError((error: unknown) => {
          const requestError = toRequestError(error);
          return requestError.kind === 'not-found'
            ? of<OverviewViewState>({ state: 'not-found' as const })
            : of<OverviewViewState>({ state: 'error' as const, error: requestError });
        }),
        startWith<OverviewViewState>({ state: 'loading' as const }),
      ),
    ),
    shareReplay({ bufferSize: 1, refCount: true }),
  );
}
