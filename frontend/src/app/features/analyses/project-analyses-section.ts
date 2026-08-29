import { AsyncPipe, DatePipe } from '@angular/common';
import { Component, inject, Input } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import {
  catchError,
  combineLatest,
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
import { LoadingIndicator } from '../../shared/components/loading-indicator';
import { AnalysisForm, Objective } from './analysis-form';
import {
  AnalysisSummary,
  CreateAnalysisRequest,
  IntentDefinition,
  Source,
} from './analysis.models';
import { AnalysisService } from './analysis.service';
import { IntentCatalogService } from './intent-catalog.service';
import { SourceService } from '../projects/source.service';

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
  imports: [AsyncPipe, DatePipe, RouterLink, AnalysisForm, LoadingIndicator],
  templateUrl: './project-analyses-section.html',
  styleUrl: './project-analyses-section.scss',
})
export class ProjectAnalysesSection {
  @Input({ required: true }) projectId = '';
  private readonly service = inject(AnalysisService);
  private readonly intentCatalog = inject(IntentCatalogService);
  private readonly sourceService = inject(SourceService);
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

  readonly objectives$: Observable<ListState<readonly Objective[]>> = this.intentCatalog
    .getSupportedIntents()
    .pipe(
      map((intents) => {
        const genericIntents = intents.filter((i) => i.executionMode === 'GENERIC');
        const objectives = this.mapIntentsToObjectives(genericIntents);
        return { state: 'loaded' as const, data: objectives };
      }),
      catchError((error: unknown) =>
        of({ state: 'error' as const, error: toRequestError(error, 'analysis') }),
      ),
      startWith({ state: 'loading' as const }),
      shareReplay({ bufferSize: 1, refCount: true }),
    );

  readonly sources$: Observable<ListState<readonly Source[]>> = this.refresh.pipe(
    startWith(undefined),
    switchMap(() =>
      this.sourceService.getSourcesByProject(this.projectId).pipe(
        map((sources: readonly { id: string; name: string; active: boolean; type: string }[]) =>
          sources
            .filter((s) => s.active && s.type === 'GIT_REPOSITORY')
            .map((s) => ({ id: s.id, name: s.name }) as Source),
        ),
        map((data) => ({ state: 'loaded' as const, data })),
        catchError((error: unknown) =>
          of({ state: 'error' as const, error: toRequestError(error, 'source') }),
        ),
        startWith({ state: 'loading' as const }),
      ),
    ),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  readonly combined$: Observable<{
    readonly objectives: ListState<readonly Objective[]>;
    readonly sources: ListState<readonly Source[]>;
  }> = combineLatest([this.objectives$, this.sources$]).pipe(
    map(([objectives, sources]) => ({ objectives, sources })),
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

  private mapIntentsToObjectives(intents: readonly IntentDefinition[]): Objective[] {
    const intentMap = new Map(intents.map((i) => [`${i.id}-${i.version}`, i]));
    const definitions = [
      {
        label: 'Understand this project',
        description: 'Get a comprehensive overview of the project across all repositories.',
        intentId: 'describe-project-v1',
        scope: 'PROJECT_SCOPE' as const,
      },
      {
        label: 'Prepare README information',
        description:
          'Generate structured information needed for a README file for a specific repository.',
        intentId: 'generate-readme-v1',
        scope: 'REPOSITORY_SCOPE' as const,
      },
      {
        label: 'Review the architecture',
        description: 'Analyze the architecture of the project across all repositories.',
        intentId: 'architecture-overview-v1',
        scope: 'PROJECT_SCOPE' as const,
      },
      {
        label: 'Analyze engineering decisions',
        description: 'Review engineering decisions made across the project.',
        intentId: 'analyze-engineering-decision-v1',
        scope: 'PROJECT_SCOPE' as const,
      },
    ];
    return definitions.filter((def) => intentMap.has(def.intentId)).map((def) => def as Objective);
  }
}
