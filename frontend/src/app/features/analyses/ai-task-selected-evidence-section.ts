import { AsyncPipe, DatePipe } from '@angular/common';
import { Component, inject, Input, OnChanges } from '@angular/core';
import {
  catchError,
  distinctUntilChanged,
  map,
  Observable,
  of,
  ReplaySubject,
  startWith,
  Subject,
  switchMap,
} from 'rxjs';
import { RequestError, toRequestError } from '../../core/http/request-error';
import { AiTaskDetail } from './analysis.models';
import {
  AiTaskSelectedEvidenceResponse,
  SelectedEvidenceCategories,
} from './ai-task-selected-evidence.models';
import { AnalysisService } from './analysis.service';

type SelectedEvidenceLoadState =
  | { readonly state: 'loading' }
  | { readonly state: 'loaded'; readonly data: AiTaskSelectedEvidenceResponse }
  | { readonly state: 'error'; readonly error: RequestError };

interface EvidenceRequestContext {
  readonly analysisId: string;
  readonly key: string;
}

@Component({
  selector: 'app-ai-task-selected-evidence-section',
  imports: [AsyncPipe, DatePipe],
  templateUrl: './ai-task-selected-evidence-section.html',
  styleUrl: './ai-task-selected-evidence-section.scss',
})
export class AiTaskSelectedEvidenceSection implements OnChanges {
  @Input({ required: true }) analysisId = '';
  @Input({ required: true }) newestTask: AiTaskDetail | null = null;

  private readonly service = inject(AnalysisService);
  private readonly requestContexts = new ReplaySubject<EvidenceRequestContext>(1);
  private readonly retryRequests = new Subject<void>();

  readonly view$: Observable<SelectedEvidenceLoadState> = this.requestContexts.pipe(
    distinctUntilChanged((previous, current) => previous.key === current.key),
    switchMap(({ analysisId }) =>
      this.retryRequests.pipe(
        startWith(undefined),
        switchMap(() =>
          this.service.getSelectedEvidence(analysisId).pipe(
            map((data) => ({ state: 'loaded' as const, data })),
            catchError((error: unknown) =>
              of({ state: 'error' as const, error: toRequestError(error, 'analysis') }),
            ),
            startWith({ state: 'loading' as const }),
          ),
        ),
      ),
    ),
  );

  ngOnChanges(): void {
    if (!this.analysisId) return;
    this.requestContexts.next({
      analysisId: this.analysisId,
      key: `${this.analysisId}:${this.taskReadinessKey(this.newestTask)}`,
    });
  }

  retry(): void {
    this.retryRequests.next();
  }

  isGloballyEmpty(categories: SelectedEvidenceCategories): boolean {
    return (
      categories.facts.count +
        categories.observations.count +
        categories.priorInsights.count +
        categories.architectureKnowledge.count +
        categories.engineeringEvents.count +
        categories.humanContext.count +
        categories.evolutionContext.count +
        categories.repositoryEvidence.count ===
      0
    );
  }

  private taskReadinessKey(task: AiTaskDetail | null): string {
    if (!task) return 'no-task';
    if (task.selectionVersion || task.selectionDigest) {
      return `${task.id}:snapshot:${task.selectionVersion ?? ''}:${task.selectionDigest ?? ''}`;
    }
    if (task.status === 'COMPLETED' || task.status === 'FAILED') {
      return `${task.id}:terminal`;
    }
    return `${task.id}:pending`;
  }
}
