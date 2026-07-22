import { AsyncPipe, DatePipe } from '@angular/common';
import { Component, inject, Input } from '@angular/core';
import {
  catchError,
  exhaustMap,
  map,
  Observable,
  of,
  scan,
  shareReplay,
  startWith,
  Subject,
  switchMap,
  tap,
} from 'rxjs';

import { RequestError, toRequestError } from '../../core/http/request-error';
import { SourceForm } from './source-form';
import { CreateSourceRequest, SourceSummary } from './source.models';
import { SourceService } from './source.service';

type SourcesViewState =
  | { readonly state: 'loading' }
  | { readonly state: 'loaded'; readonly sources: readonly SourceSummary[] }
  | { readonly state: 'error'; readonly error: RequestError };

type SourceMutationAction =
  | { readonly kind: 'create'; readonly request: CreateSourceRequest }
  | {
      readonly kind: 'activation';
      readonly sourceId: string;
      readonly sourceName: string;
      readonly active: boolean;
    };

type MutationEvent =
  | { readonly state: 'pending'; readonly action: SourceMutationAction }
  | { readonly state: 'success'; readonly action: SourceMutationAction; readonly message: string }
  | {
      readonly state: 'error';
      readonly action: SourceMutationAction;
      readonly error: RequestError;
    };

type MutationViewState =
  | { readonly state: 'idle'; readonly resetToken: number }
  | (MutationEvent & { readonly resetToken: number });

const initialMutationState: MutationViewState = { state: 'idle', resetToken: 0 };

@Component({
  selector: 'app-project-sources-section',
  imports: [AsyncPipe, DatePipe, SourceForm],
  templateUrl: './project-sources-section.html',
  styleUrl: './project-sources-section.scss',
})
export class ProjectSourcesSection {
  @Input({ required: true }) projectId = '';

  private readonly sourceService = inject(SourceService);
  private readonly refreshSources = new Subject<void>();
  private readonly mutationActions = new Subject<SourceMutationAction>();

  readonly sourcesViewModel$: Observable<SourcesViewState> = this.refreshSources.pipe(
    startWith(undefined),
    switchMap(() =>
      this.sourceService.getSourcesByProject(this.projectId).pipe(
        map((sources) => ({ state: 'loaded' as const, sources })),
        catchError((error: unknown) =>
          of({ state: 'error' as const, error: toRequestError(error, 'sources') }),
        ),
        startWith({ state: 'loading' as const }),
      ),
    ),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  readonly mutationViewModel$: Observable<MutationViewState> = this.mutationActions.pipe(
    exhaustMap((action) => {
      const mutation$ =
        action.kind === 'create'
          ? this.sourceService.createSource(action.request)
          : this.sourceService.setSourceActive(action.sourceId, action.active);

      return mutation$.pipe(
        tap(() => this.refreshSources.next()),
        map((): MutationEvent => ({
          state: 'success',
          action,
          message:
            action.kind === 'create'
              ? 'Source created successfully.'
              : `${action.sourceName} is now ${action.active ? 'active' : 'inactive'}.`,
        })),
        catchError((error: unknown) =>
          of<MutationEvent>({
            state: 'error',
            action,
            error: toRequestError(error, 'source'),
          }),
        ),
        startWith<MutationEvent>({ state: 'pending', action }),
      );
    }),
    scan<MutationEvent, MutationViewState>((current, event) => {
      const resetToken =
        event.state === 'success' && event.action.kind === 'create'
          ? current.resetToken + 1
          : current.resetToken;
      return { ...event, resetToken };
    }, initialMutationState),
    startWith(initialMutationState),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  createSource(request: CreateSourceRequest): void {
    this.mutationActions.next({ kind: 'create', request });
  }

  setActive(source: SourceSummary, active: boolean): void {
    this.mutationActions.next({
      kind: 'activation',
      sourceId: source.id,
      sourceName: source.name,
      active,
    });
  }
}
