import { AsyncPipe, DatePipe } from '@angular/common';
import { Component, EventEmitter, inject, Input, OnChanges, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  BehaviorSubject,
  catchError,
  distinctUntilChanged,
  exhaustMap,
  map,
  merge,
  Observable,
  of,
  shareReplay,
  startWith,
  Subject,
  switchMap,
} from 'rxjs';
import { RequestError, toRequestError } from '../../core/http/request-error';
import { ProjectFreshnessResponse } from './project-freshness.models';
import { ProjectFreshnessService } from './project-freshness.service';
import { SourceSummary } from './source.models';

type FreshnessView =
  | { readonly state: 'unchecked' }
  | { readonly state: 'loading' }
  | { readonly state: 'checking' }
  | { readonly state: 'ready'; readonly data: ProjectFreshnessResponse }
  | {
      readonly state: 'error';
      readonly error: RequestError;
      readonly previous?: ProjectFreshnessResponse;
    };

@Component({
  selector: 'app-project-freshness-section',
  imports: [AsyncPipe, DatePipe, FormsModule, RouterLink],
  templateUrl: './project-freshness-section.html',
  styleUrl: './project-freshness-section.scss',
})
export class ProjectFreshnessSection implements OnChanges {
  @Input({ required: true }) projectId = '';
  @Input({ required: true }) sources: readonly SourceSummary[] = [];
  @Output() refreshRequested = new EventEmitter<string>();

  private readonly service = inject(ProjectFreshnessService);
  private readonly selected = new BehaviorSubject('');
  private readonly checks = new Subject<string>();
  selectedSourceId = '';

  readonly view$: Observable<FreshnessView> = merge(
    this.selected.pipe(
      distinctUntilChanged(),
      switchMap((sourceId) =>
        sourceId
          ? this.service.getLatest(this.projectId, sourceId).pipe(
              map((data): FreshnessView =>
                data ? { state: 'ready', data } : { state: 'unchecked' },
              ),
              catchError((error: unknown) =>
                of<FreshnessView>({ state: 'error', error: toRequestError(error, 'project') }),
              ),
              startWith<FreshnessView>({ state: 'loading' }),
            )
          : of<FreshnessView>({ state: 'unchecked' }),
      ),
    ),
    this.checks.pipe(
      exhaustMap((sourceId) =>
        this.service.check(this.projectId, sourceId).pipe(
          map((data): FreshnessView => ({ state: 'ready', data })),
          catchError((error: unknown) =>
            of<FreshnessView>({ state: 'error', error: toRequestError(error, 'project') }),
          ),
          startWith<FreshnessView>({ state: 'checking' }),
        ),
      ),
    ),
  ).pipe(shareReplay({ bufferSize: 1, refCount: true }));

  get compatibleSources(): readonly SourceSummary[] {
    return this.sources.filter((source) => source.active && source.type === 'GIT_REPOSITORY');
  }

  ngOnChanges(): void {
    const compatible = this.compatibleSources;
    if (compatible.length === 1) this.select(compatible[0].id);
    else if (!compatible.some((source) => source.id === this.selectedSourceId)) this.select('');
  }

  select(sourceId: string): void {
    this.selectedSourceId = sourceId;
    this.selected.next(sourceId);
  }

  check(): void {
    if (this.selectedSourceId) this.checks.next(this.selectedSourceId);
  }

  requestRefresh(): void {
    if (this.selectedSourceId) this.refreshRequested.emit(this.selectedSourceId);
  }

  short(revision: string | null | undefined): string {
    return revision ? revision.slice(0, 12) : 'Unavailable';
  }
}
