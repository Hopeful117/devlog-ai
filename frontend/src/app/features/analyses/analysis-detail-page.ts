import { AsyncPipe, DatePipe, JsonPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  catchError,
  exhaustMap,
  map,
  Observable,
  of,
  shareReplay,
  startWith,
  Subject,
  switchMap,
  takeWhile,
  timer,
} from 'rxjs';
import { APP_ENVIRONMENT } from '../../core/config/app-environment';
import { RequestError, toRequestError } from '../../core/http/request-error';
import {
  AnalysisDetail,
  AnalysisDiagnostics,
  AiTaskDetail,
  CollectionWarning,
  JsonValue,
  ProjectProfile,
} from './analysis.models';
import { AnalysisService } from './analysis.service';
import { AnalysisInsightsSection } from '../insights/analysis-insights-section';
import { LoadingIndicator } from '../../shared/components/loading-indicator';

type LoadState<T> =
  | { readonly state: 'loading' }
  | { readonly state: 'loaded'; readonly data: T }
  | { readonly state: 'not-found' }
  | { readonly state: 'error'; readonly error: RequestError };

@Component({
  selector: 'app-analysis-detail-page',
  imports: [AsyncPipe, DatePipe, JsonPipe, RouterLink, AnalysisInsightsSection, LoadingIndicator],
  templateUrl: './analysis-detail-page.html',
  styleUrl: './analysis-detail-page.scss',
})
export class AnalysisDetailPage {
  private readonly route = inject(ActivatedRoute);
  private readonly service = inject(AnalysisService);
  private readonly intervalMs = inject(APP_ENVIRONMENT).analysisPollingIntervalMs;
  private readonly diagnosticsRefresh = new Subject<void>();
  private readonly executionRefresh = new Subject<void>();
  private readonly routeId$ = this.route.paramMap.pipe(
    map((params) => params.get('id') ?? ''),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  readonly analysis$: Observable<LoadState<AnalysisDetail>> = this.routeId$.pipe(
    switchMap((id) =>
      this.service.getAnalysis(id).pipe(
        map((data) => ({ state: 'loaded' as const, data })),
        catchError((error: unknown) => {
          const mapped = toRequestError(error, 'analysis');
          return of(
            mapped.kind === 'not-found'
              ? { state: 'not-found' as const }
              : { state: 'error' as const, error: mapped },
          );
        }),
        startWith({ state: 'loading' as const }),
      ),
    ),
  );

  readonly diagnostics$: Observable<LoadState<AnalysisDiagnostics>> = this.routeId$.pipe(
    switchMap((id) =>
      this.diagnosticsRefresh.pipe(
        startWith(undefined),
        switchMap(() =>
          timer(0, this.intervalMs).pipe(
            exhaustMap(() =>
              this.service.getDiagnostics(id).pipe(
                map(
                  (data) => ({ state: 'loaded' as const, data }) as LoadState<AnalysisDiagnostics>,
                ),
                catchError((error: unknown) =>
                  of({ state: 'error' as const, error: toRequestError(error, 'diagnostics') }),
                ),
              ),
            ),
            takeWhile(
              (result) =>
                result.state === 'loaded' &&
                result.data.identity.status !== 'COMPLETED' &&
                result.data.identity.status !== 'FAILED',
              true,
            ),
            startWith({ state: 'loading' as const }),
          ),
        ),
      ),
    ),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  readonly aiTasks$: Observable<LoadState<readonly AiTaskDetail[]>> = this.routeId$.pipe(
    switchMap((id) =>
      this.executionRefresh.pipe(
        startWith(undefined),
        switchMap(() =>
          timer(0, this.intervalMs).pipe(
            exhaustMap(() =>
              this.service.getAiTasksByAnalysis(id).pipe(
                map(
                  (data) =>
                    ({ state: 'loaded' as const, data }) as LoadState<readonly AiTaskDetail[]>,
                ),
                catchError((error: unknown) =>
                  of({ state: 'error' as const, error: toRequestError(error, 'analysis') }),
                ),
              ),
            ),
            takeWhile((result) => {
              if (result.state !== 'loaded') return false;
              const latest = result.data[0];
              return !latest || (latest.status !== 'COMPLETED' && latest.status !== 'FAILED');
            }, true),
            startWith({ state: 'loading' as const }),
          ),
        ),
      ),
    ),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  readonly warnings$ = this.resource((id) => this.service.getWarnings(id));
  readonly profile$ = this.resource((id) => this.service.getProfile(id));
  readonly context$ = this.resource((id) => this.service.getContext(id));

  refreshDiagnostics(): void {
    this.diagnosticsRefresh.next();
  }

  refreshExecution(): void {
    this.executionRefresh.next();
  }

  private resource<T>(load: (id: string) => Observable<T>): Observable<LoadState<T>> {
    return this.routeId$.pipe(
      switchMap((id) =>
        load(id).pipe(
          map((data) => ({ state: 'loaded' as const, data })),
          catchError((error: unknown) =>
            of({ state: 'error' as const, error: toRequestError(error, 'analysis') }),
          ),
          startWith({ state: 'loading' as const }),
        ),
      ),
    );
  }
}
