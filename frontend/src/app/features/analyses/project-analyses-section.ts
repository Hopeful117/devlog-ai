import { AsyncPipe, DatePipe } from '@angular/common';
import { Component, inject, Input } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import {
  catchError,
  concatMap,
  exhaustMap,
  map,
  Observable,
  of,
  shareReplay,
  startWith,
  Subject,
  switchMap,
  tap,
} from 'rxjs';
import { RequestError, toRequestError } from '../../core/http/request-error';
import { AnalysisForm } from './analysis-form';
import { AnalysisSummary, CreateAnalysisRequest, IntentDefinition } from './analysis.models';
import { AnalysisService } from './analysis.service';
import { IntentCatalogService } from './intent-catalog.service';

type ListState<T> =
  | { readonly state: 'loading' }
  | { readonly state: 'loaded'; readonly data: T }
  | { readonly state: 'error'; readonly error: RequestError };
type LaunchState =
  | { readonly state: 'idle' }
  | { readonly state: 'pending' }
  | { readonly state: 'error'; readonly error: RequestError };

@Component({
  selector: 'app-project-analyses-section',
  imports: [AsyncPipe, DatePipe, RouterLink, AnalysisForm],
  templateUrl: './project-analyses-section.html',
  styleUrl: './project-analyses-section.scss',
})
export class ProjectAnalysesSection {
  @Input({ required: true }) projectId = '';
  private readonly service = inject(AnalysisService);
  private readonly intentCatalog = inject(IntentCatalogService);
  private readonly router = inject(Router);
  private readonly refresh = new Subject<void>();
  private readonly launches = new Subject<CreateAnalysisRequest>();
  showForm = false;

  readonly analyses$: Observable<ListState<readonly AnalysisSummary[]>> = this.refresh.pipe(
    startWith(undefined),
    switchMap(() =>
      this.service.getAnalysesByProject(this.projectId).pipe(
        map((data) => ({ state: 'loaded' as const, data })),
        catchError((error: unknown) =>
          of({ state: 'error' as const, error: toRequestError(error, 'analysis') }),
        ),
        startWith({ state: 'loading' as const }),
      ),
    ),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  readonly intents$: Observable<ListState<readonly IntentDefinition[]>> = this.intentCatalog
    .getSupportedIntents()
    .pipe(
      map((data) => ({ state: 'loaded' as const, data })),
      catchError((error: unknown) =>
        of({ state: 'error' as const, error: toRequestError(error, 'analysis') }),
      ),
      startWith({ state: 'loading' as const }),
      shareReplay({ bufferSize: 1, refCount: true }),
    );

  readonly launchState$: Observable<LaunchState> = this.launches.pipe(
    exhaustMap((request) =>
      this.service.createAnalysis(request).pipe(
        concatMap((created) => this.service.launchAnalysis(created.id).pipe(map(() => created))),
        tap((created) => {
          this.refresh.next();
          void this.router.navigate(['/analyses', created.id]);
        }),
        map((): LaunchState => ({ state: 'idle' })),
        catchError((error: unknown) =>
          of<LaunchState>({ state: 'error', error: toRequestError(error, 'analysis') }),
        ),
        startWith<LaunchState>({ state: 'pending' }),
      ),
    ),
    startWith<LaunchState>({ state: 'idle' }),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  launch(request: CreateAnalysisRequest): void {
    this.launches.next(request);
  }
}
