import { AsyncPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { catchError, map, of, shareReplay, startWith, switchMap } from 'rxjs';

import { RequestError, toRequestError } from '../../core/http/request-error';
import { ProjectService } from '../projects/project.service';
import { TimelineService } from './timeline.service';

type TimelineViewState =
  | { readonly state: 'loading' }
  | {
      readonly state: 'loaded';
      readonly projectName: string;
      readonly entries: readonly import('./timeline.models').TimelineEntry[];
    }
  | { readonly state: 'not-found' }
  | { readonly state: 'error'; readonly error: RequestError };

@Component({
  selector: 'app-timeline-page',
  imports: [AsyncPipe],
  templateUrl: './timeline-page.html',
  styleUrl: './timeline-page.scss',
})
export class TimelinePage {
  private readonly route = inject(ActivatedRoute);
  private readonly projectService = inject(ProjectService);
  private readonly timelineService = inject(TimelineService);

  readonly viewModel$ = this.route.paramMap.pipe(
    map((params) => params.get('id') ?? ''),
    switchMap((identifier) =>
      this.projectService.getProject(identifier).pipe(
        switchMap((project) =>
          this.timelineService.getTimeline(project.id).pipe(
            map((timeline) => ({
              state: 'loaded' as const,
              projectName: project.name,
              entries: timeline.entries,
            })),
            catchError((error: unknown) =>
              of<TimelineViewState>({ state: 'error' as const, error: toRequestError(error) }),
            ),
          ),
        ),
        catchError((error: unknown) => {
          const requestError = toRequestError(error);
          return requestError.kind === 'not-found'
            ? of<TimelineViewState>({ state: 'not-found' as const })
            : of<TimelineViewState>({ state: 'error' as const, error: requestError });
        }),
        startWith<TimelineViewState>({ state: 'loading' as const }),
      ),
    ),
    shareReplay({ bufferSize: 1, refCount: true }),
  );
}
